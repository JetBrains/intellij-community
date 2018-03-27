/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType

/**
 * @author nik
 */
@CompileStatic
class JdkUtils {
  static void defineJdk(JpsGlobal global, String jdkName, String jdkHomePath) {
    def sdk = JpsJavaExtensionService.instance.addJavaSdk(global, jdkName, jdkHomePath)
    def toolsJar = new File(jdkHomePath, "lib/tools.jar")
    if (toolsJar.exists()) {
      sdk.addRoot(toolsJar, JpsOrderRootType.COMPILED)
    }
  }

  static String computeJdkHome(BuildMessages messages, String propertyName, String defaultDir, String envVarName) {
    String jdkDir = System.getProperty(propertyName)
    if (jdkDir != null) {
      return jdkDir
    }

    jdkDir = SystemInfo.isMac ? "$defaultDir/Home" : defaultDir
    if (new File(jdkDir).exists()) {
      messages.info("$propertyName set to $jdkDir")
    }
    else {
      jdkDir = System.getenv(envVarName)
      if (jdkDir != null) {
        messages.info("'$defaultDir' doesn't exist, $propertyName set to '$envVarName' environment variable: $jdkDir")
      }
      else {
        jdkDir = getCurrentJdk()
        def jdkInfo = JdkVersionDetector.instance.detectJdkVersionInfo(jdkDir)
        if (propertyName.contains("8") && !jdkInfo.version.contains("1.8.")) {
          messages.error("JDK 1.8 is required to compile the project, but '$propertyName' property and '$envVarName' environment variable" +
                         " aren't defined and default JDK $jdkDir ($jdkInfo) cannot be used as JDK 1.8")
          return null
        }
        messages.info("'$envVarName' isn't defined and '$defaultDir' doesn't exist, $propertyName set to $jdkDir")
      }
    }
    return jdkDir
  }

  private static String getCurrentJdk() {
    def javaHome = SystemProperties.javaHome
    if (new File(javaHome).name == "jre") {
      return new File(javaHome).getParent()
    }
    return javaHome
  }
}
