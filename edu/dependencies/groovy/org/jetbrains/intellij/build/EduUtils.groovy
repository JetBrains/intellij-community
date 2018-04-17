package org.jetbrains.intellij.build
/*
 * Copyright 2000-2018 JetBrains s.r.o.
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


import com.intellij.util.SystemProperties
import com.intellij.util.io.ZipUtil

class EduUtils {
  static void copyEduToolsPlugin(String dependenciesPath, BuildContext buildContext, String targetDirectory) {
    def dependenciesProjectDir = new File(dependenciesPath)
    new GradleRunner(dependenciesProjectDir, buildContext.messages, SystemProperties.getJavaHome()).run("Downloading EduTools plugin...", "setupEduPlugin")
    Properties properties = new Properties()
    new File(dependenciesProjectDir, "gradle.properties").withInputStream {
      properties.load(it)
    }

    def pluginZip = new File("${dependenciesProjectDir.absolutePath}/build/edu/EduTools-${properties.getProperty("eduPluginVersion")}.zip")
    if (!pluginZip.exists()) {
      throw new IllegalStateException("EduTools bundled plugin is not found. Plugin path:${pluginZip.canonicalPath}")
    }
    ZipUtil.extract(pluginZip, new File("$targetDirectory/plugins/"), new FilenameFilter() {
      @Override
      boolean accept(File dir, String name) {
        return true
      }
    })
  }
}