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
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.gant.Log4jFileLoggerFactory

/**
 * @author nik
 */
class JpsCompilationData {
  final File dataStorageRoot
  final Set<String> compiledModules = new HashSet<>()
  final Set<String> compiledModuleTests = new HashSet<>()
  Logger.Factory fileLoggerFactory
  boolean statisticsReported

  JpsCompilationData(File dataStorageRoot, File buildLogFile, String categoriesWithDebugLevel, BuildMessages messages) {
    this.dataStorageRoot = dataStorageRoot
    categoriesWithDebugLevel = categoriesWithDebugLevel ?: ""
    try {
      fileLoggerFactory = new Log4jFileLoggerFactory(buildLogFile, categoriesWithDebugLevel)
      messages.info("Build log (${!categoriesWithDebugLevel.isEmpty() ? "debug level for $categoriesWithDebugLevel" : "info"}) will be written to $buildLogFile.absolutePath")
    }
    catch (Throwable t) {
      messages.warning("Cannot setup additional logging to $buildLogFile.absolutePath: $t.message")
    }
  }
}
