// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author Eugene Zhuravlev
 */
public interface GlobalOptions {

  String COMPILE_PARALLEL_OPTION = "compile.parallel";
  String ALLOW_PARALLEL_AUTOMAKE_OPTION = "allow.parallel.automake";
  String COMPILE_PARALLEL_MAX_THREADS_OPTION = "compile.parallel.max.threads";
  String REBUILD_ON_DEPENDENCY_CHANGE_OPTION = "rebuild.on.dependency.change";
  String LOG_DIR_OPTION = "jps.log.dir";
  String FALLBACK_JDK_HOME = "jps.fallback.jdk.home";
  String FALLBACK_JDK_VERSION = "jps.fallback.jdk.version";
  String REPORT_BUILD_STATISTICS = "jps.report.build.statistics";
  String JPS_IN_WSL_OPTION = "jps.in.wsl";
  String DEPENDENCY_GRAPH_ENABLED = "jps.use.dependency.graph";

  /**
   * Set this property to 'false' to disable default logging. By default the log is written to build.log file in the directory specified by {@link #LOG_DIR_OPTION}.
   */
  String USE_DEFAULT_FILE_LOGGING_OPTION = "jps.use.default.file.logging";

  // builder ID for all global build messages sent to the controlling IDE
  String JPS_SYSTEM_BUILDER_ID = "JPS";
  // notification about the files changed during compilation, but not compiled in current compilation session
  String JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID = "!unprocessed_fs_changes_detected!";

  /**
   * The path to external project config directory (used for external system projects).
   */
  String EXTERNAL_PROJECT_CONFIG = "external.project.config";

  /**
   * The path to optional localization language bundle currently used by IDE.
   * This will allow JPS process to access bundle's resources and provide localized error/warning/diagnostic messages
   */
  String LANGUAGE_BUNDLE = "jps.language.bundle";

  /**
   * Environment variable set to UNIX timestamp, defined as the number of seconds, excluding leap seconds, since 01 Jan 1970 00:00:00 UTC.
   * Should be used instead of a current time for build process to have deterministic timestamps in artifacts like installer distributions.
   * See <a href="https://reproducible-builds.org/specs/source-date-epoch/">specification</a>
   */
  String BUILD_DATE_IN_SECONDS = "SOURCE_DATE_EPOCH";

  /**
   * A key to specify if the in-memory logger should be used for failed builds.
   * When set to true, this flag enables the use of an in-memory mechanism to log
   * the details only of failed builds on disk.
   */
  @ApiStatus.Experimental
  String USE_IN_MEMORY_FAILED_BUILD_LOGGER = "jps.use.in.memory.failed.build.logger";
}
