package org.jetbrains.jps.api;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/24/12
 */
public interface GlobalOptions {
  String USE_MEMORY_TEMP_CACHE_OPTION = "use.memory.temp.cache";
  String USE_EXTERNAL_JAVAC_OPTION = "use.external.javac.process";
  String GENERATE_CLASSPATH_INDEX_OPTION = "generate.classpath.index";
  String COMPILE_PARALLEL_OPTION = "compile.parallel";
  String COMPILE_PARALLEL_MAX_THREADS_OPTION = "compile.parallel.max.threads";
}
