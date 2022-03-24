// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.io.ZipUtil
import groovy.transform.CompileStatic

@CompileStatic
final class ExternalPluginBundler {
  static void bundle(String pluginName,
                     String dependenciesPath,
                     BuildContext buildContext,
                     String targetDirectory,
                     String buildTaskName = pluginName) {
    def dependenciesProjectDir = new File(dependenciesPath)
    new GradleRunner(dependenciesProjectDir, buildContext.paths.projectHome, buildContext.messages, buildContext.options).run(
      "Downloading $pluginName plugin...", "setup${buildTaskName}Plugin")
    Properties properties = new Properties()
    new File(dependenciesProjectDir, "gradle.properties").withInputStream {
      properties.load(it)
    }

    File pluginZip = new File("${dependenciesProjectDir.absolutePath}/build/$pluginName/$pluginName-${properties.getProperty("${buildTaskName}PluginVersion")}.zip")
    if (!pluginZip.exists()) {
      throw new IllegalStateException("$pluginName bundled plugin is not found. Plugin path:${pluginZip.canonicalPath}")
    }
    extractPlugin(pluginZip, targetDirectory)
  }

  static void extractPlugin(File pluginZip, String targetDirectory) {
    ZipUtil.extract(pluginZip, new File("$targetDirectory/plugins/"), new FilenameFilter() {
      @Override
      boolean accept(File dir, String name) {
        return true
      }
    })
  }
}