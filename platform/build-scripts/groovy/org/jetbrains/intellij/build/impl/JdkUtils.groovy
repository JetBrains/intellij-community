// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType

import static com.intellij.util.io.URLUtil.*

@CompileStatic
class JdkUtils {
  static void defineJdk(JpsGlobal global, String jdkName, String jdkHomePath, BuildMessages messages) {
    def sdk = JpsJavaExtensionService.instance.addJavaSdk(global, jdkName, jdkHomePath)
    def toolsJar = new File(jdkHomePath, "lib/tools.jar")
    if (toolsJar.exists()) {
      sdk.addRoot(toolsJar, JpsOrderRootType.COMPILED)
    }
    messages.info("'$jdkName' Java SDK set to $jdkHomePath")
  }

  static String computeJdkHome(BuildMessages messages, String name, String defaultDir, String envVarName) {
    String jdkDir
    if (defaultDir != null) {
      jdkDir = SystemInfo.isMac ? "$defaultDir/Contents/Home" : defaultDir
      if (new File(jdkDir).exists()) {
        return jdkDir
      }
      else {
        jdkDir = null
      }
    }
    messages.info("$name: dir=$defaultDir, env=$envVarName")
    if (envVarName != null) {
      jdkDir = System.getenv(envVarName)
    }
    if (jdkDir == null) {
      jdkDir = getCurrentJdk()
      def jdkInfo = JdkVersionDetector.instance.detectJdkVersionInfo(jdkDir)
      def jdkVersion = jdkInfo.@version.feature.toString()
      if (!name.contains(jdkVersion)) {
        messages.warning("JDK $name is required, but default JDK $jdkDir ($jdkInfo) cannot be used as JDK $name")
      }
    }
    messages.info("$name set to $jdkDir")
    return jdkDir
  }

  static String getCurrentJdk() {
    def javaHome = SystemProperties.javaHome
    if (new File(javaHome).name == "jre") {
      return new File(javaHome).getParent()
    }
    return javaHome
  }

  /**
   * Code is copied from com.intellij.openapi.projectRoots.impl.JavaSdkImpl#findClasses(java.io.File, boolean)
   */
  static List<String> readModulesFromReleaseFile(File jbrBaseDir) {
    File releaseFile = new File(jbrBaseDir, "release")
    if (!releaseFile.exists()) return Collections.<String>emptyList()
    new FileInputStream(releaseFile).withReader { stream ->
      Properties p = new Properties()
      p.load(stream)
      String jbrBaseUrl = JRT_PROTOCOL + SCHEME_SEPARATOR +
                          FileUtil.toSystemIndependentName(jbrBaseDir.absolutePath) +
                          JAR_SEPARATOR
      def modules = p.getProperty("MODULES")
      return modules == null ? Collections.<String>emptyList() : StringUtil
        .split(StringUtil.unquoteString(modules), " ")
        .collect { jbrBaseUrl + it }
    }
  }
}
