/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 *         Date: 1/24/12
 */
public interface GlobalOptions {
  String USE_MEMORY_TEMP_CACHE_OPTION = "use.memory.temp.cache";
  String USE_EXTERNAL_JAVAC_OPTION = "use.external.javac.process";
  String GENERATE_CLASSPATH_INDEX_OPTION = "generate.classpath.index";
  String COMPILE_PARALLEL_OPTION = "compile.parallel";
  String COMPILE_PARALLEL_MAX_THREADS_OPTION = "compile.parallel.max.threads";
  String REBUILD_ON_DEPENDENCY_CHANGE_OPTION = "rebuild.on.dependency.change";
  String LOG_DIR_OPTION = "log.dir";
}
