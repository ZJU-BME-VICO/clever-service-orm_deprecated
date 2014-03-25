package edu.zju.bme.clever.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.ast.ASTQueryTranslatorFactory;
import org.hibernate.hql.spi.QueryTranslator;
import org.hibernate.hql.spi.QueryTranslatorFactory;
import org.openehr.am.parser.ContentObject;
import org.openehr.am.parser.DADLParser;
import org.openehr.rm.binding.DADLBinding;
import org.openehr.rm.common.archetyped.Locatable;

import edu.zju.bme.archetype2java.Archetype2Java;

public enum CleverServiceSingleton {

	INSTANCE;

	private Logger logger = Logger.getLogger(CleverServiceSingleton.class.getName());

	private Configuration cfg;
	private SessionFactory sessionFactory;

	private boolean serviceStatus = false;

	private CleverServiceSingleton() {
	}

	public int start() {

		logger.info("start");
		serviceStatus = true;
		return 0;

	}

	public int stop() {

		logger.info("stop");
		serviceStatus = false;
		return 0;

	}

	public boolean getServiceStatus() {

		logger.info(serviceStatus);
		return serviceStatus;

	}

	public int reconfigure(Collection<String> archetypes, Collection<String> arms) {

		logger.info("reconfigure");

		try {
			if (getServiceStatus()) {
				return -1;
			}

			if (sessionFactory != null) {
				sessionFactory.close();
			}

			cfg = new Configuration().configure();
			
			Archetype2Java.INSTANCE.setClassFilePath(Thread.currentThread().getContextClassLoader().getResource("").getPath());
			Archetype2Java.INSTANCE.setSourceFilePath(Thread.currentThread().getContextClassLoader().getResource("").getPath());

			archetypes.forEach(a -> {
				Archetype2Java.INSTANCE.addArchetype(a, "edu.zju.bme.clever.service.model");
			});
			Map<String, Class<?>> classes = Archetype2Java.INSTANCE.compile();
			classes.values().forEach(c -> {
				cfg.addAnnotatedClass(c);
			});

			StandardServiceRegistry serviceRegistry = 
					new StandardServiceRegistryBuilder().applySettings(cfg.getProperties()).build();
			sessionFactory = cfg.buildSessionFactory(serviceRegistry);

			return 0;
		} catch (Exception e) {
			logger.error(e);
			return -2;
		}

	}

	public List<String> select(String aql) {

		return select(aql, null);

	}

	public List<String> select(String aql, Map<String, Object> parameters) {

		logger.info("select");

		logger.info(aql);

		try {
			if (!getServiceStatus()) {
				return null;
			}

			long startTime = System.currentTimeMillis();
			
			Session s = sessionFactory.openSession();
			Transaction txn = s.beginTransaction();

			Query q = s.createQuery(aql);
			passParameters(q, parameters);
			@SuppressWarnings("rawtypes")
			List results = q.list();

			s.flush();
			txn.commit();
			s.close();
			
			long endTime = System.currentTimeMillis();
			logger.info("aql execute time (ms) : " + (endTime - startTime));

			startTime = System.currentTimeMillis();
			
			List<String> dadlResults = new ArrayList<String>();
			for (Object arr : results) {
				if (arr.getClass().isArray()) {
					for (int i = 0; i < Array.getLength(arr); i++) {
						generateReturnDADL(Array.get(arr, i), dadlResults);
					}
				} else {
					generateReturnDADL(arr, dadlResults);
				}
			}

			endTime = System.currentTimeMillis();
			logger.info("generate dadl time (ms) : " + (endTime - startTime));
			
			return dadlResults;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	protected void generateReturnDADL(Object obj, List<String> dadlResults)
			throws Exception {

		if (obj instanceof Locatable) {
			DADLBinding binding = new DADLBinding();
			Locatable loc = (Locatable) obj;
			String str = binding.toDADLString(loc);
			if (!dadlResults.contains(str)) {
				logger.info(str);
				dadlResults.add(str);

				for (Object associatedObject : loc.getAssociatedObjects().values()) {
					generateReturnDADL(associatedObject, dadlResults);
				}
			}
		}

	}

	public int insert(List<String> dadls) {

		logger.info("insert");

		try {
			if (!getServiceStatus()) {
				return -1;
			}

			long startTime = System.currentTimeMillis();

			List<Object> objects = new ArrayList<Object>();

			for (String dadl : dadls) {
				logger.info(dadl);
				InputStream is = new ByteArrayInputStream(dadl.getBytes("UTF-8"));
				DADLParser parser = new DADLParser(is);
				ContentObject contentObj = parser.parse();
				DADLBinding binding = new DADLBinding();
				Object bp = binding.bind(contentObj);
				objects.add(bp);
			}

			Session s = sessionFactory.openSession();
			Transaction txn = s.beginTransaction();

			for (Object object : objects) {
				s.save(object);
			}

			s.flush();
			txn.commit();
			s.close();
			
			long endTime = System.currentTimeMillis();
			logger.info("aql execute time (ms) : " + (endTime - startTime));
		} catch (Exception e) {
			logger.error(e);
			return -2;
		}

		return 0;

	}

	public int delete(String aql) {

		return delete(aql, null);

	}

	public int delete(String aql, Map<String, Object> parameters) {

		return executeUpdate(aql, parameters);

	}

	public int update(String aql) {

		return update(aql, null);

	}

	public int update(String aql, Map<String, Object> parameters) {

		return executeUpdate(aql, parameters);

	}

	protected int executeUpdate(String aql, Map<String, Object> parameters) {

		logger.info("executeUpdate");

		logger.info(aql);

		try {
			if (!getServiceStatus()) {
				return -1;
			}

			long startTime = System.currentTimeMillis();

			Session s = sessionFactory.openSession();
			Transaction txn = s.beginTransaction();

			Query q = s.createQuery(aql);
			passParameters(q, parameters);
			int ret = q.executeUpdate();

			s.flush();
			txn.commit();
			s.close();
			
			long endTime = System.currentTimeMillis();
			logger.info("aql execute time (ms) : " + (endTime - startTime));

			logger.info(ret);

			return ret;
		} catch (Exception e) {
			logger.error(e);
			return -2;
		}

	}

	protected void passParameters(Query q, Map<String, Object> parameters) {

		if (parameters != null) {
			parameters.keySet().forEach(p -> {
				q.setParameter(p, parameters.get(p));				
			});
		}

	}

	public List<String> getSQL(String aql) {

		logger.info("getSQL");

		try {
			if (!getServiceStatus()) {
				return null;
			}

			if (aql == null || aql.trim().length() <= 0) {
				return null;
			}

			long startTime = System.currentTimeMillis();

			final QueryTranslatorFactory translatorFactory = new ASTQueryTranslatorFactory();
			final SessionFactoryImplementor factory = (SessionFactoryImplementor) sessionFactory;
			final QueryTranslator translator = translatorFactory
					.createQueryTranslator(aql, aql, Collections.EMPTY_MAP,
							factory, null);
			translator.compile(Collections.EMPTY_MAP, false);
			List<String> sqls = translator.collectSqlStrings();
			
			long endTime = System.currentTimeMillis();
			logger.info("aql execute time (ms) : " + (endTime - startTime));

			return sqls;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public Set<String> getArchetypeIds() {

		logger.info("getArchetypes");
		
		try {
			if (!getServiceStatus()) {
				return null;
			}
			
			return null;
			
		} catch (Exception e) {
			logger.error(e);
			return null;
		}
		
	}
	
	public String getArchetypeString(String archetypeId) {
		
		logger.info("getArchetypeString");
		
		try {
			if (!getServiceStatus()) {
				return "";
			}
			
			return null;
			
		} catch (Exception e) {
			logger.error(e);
			return "";
		}
		
	}

}