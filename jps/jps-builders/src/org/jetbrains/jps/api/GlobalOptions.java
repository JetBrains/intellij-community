/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.api;

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
   * See https://reproducible-builds.org/specs/source-date-epoch/
   */
  String BUILD_DATE_IN_SECONDS = "SOURCE_DATE_EPOCH";
}
