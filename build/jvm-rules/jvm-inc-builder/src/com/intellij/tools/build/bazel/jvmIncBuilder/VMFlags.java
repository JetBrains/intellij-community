package com.intellij.tools.build.bazel.jvmIncBuilder;

public interface VMFlags {
  /**
   * Specify how many changed source files should trigger full target rebuild instead of building the target incrementally.
   * @return Percentage of changed sources out of total sources count, that should trigger full rebuild.
   * If percent of changed sources in a target exceeds the value returned by this property, the target is fully rebuild from scratch.
   */
  static int getChangesPercentToRebuild() {
    return Integer.parseInt(System.getProperty("jvm-inc-builder.rebuild.changes.percent", "85"));
  }

  /**
   * Specify size of the cache for readonly library graphs loaded from ABI jars.
   * @return the number of cached graphs to store in memory
   */
  static int getLibraryGraphCacheSize() {
    return Integer.parseInt(System.getProperty("jvm-inc-builder.library.graph.cache.size", "1024"));
  }

  /**
   * Enable logging of build process stages. This data is required primarily for incremental tests that track the process of incremental builds.
   * To make test checks run in deterministic way, these logs follow strict output structure and are different from regular or diagnostic logs that builder collects in 'normal' operation.
   * @return true if build process logs should be collected
   */
  static boolean isBuildProcessLoggerEnabled() {
    return Boolean.parseBoolean(System.getProperty("jvm-inc-builder.log.build.process", "false"));
  }
}
