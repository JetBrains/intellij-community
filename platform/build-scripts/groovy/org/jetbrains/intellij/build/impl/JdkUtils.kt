// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream

object JdkUtils {
  fun defineJdk(global: JpsGlobal, jdkName: String, jdkHomePath: String, messages: BuildMessages) {
    val sdk = JpsJavaExtensionService.getInstance().addJavaSdk(global, jdkName, jdkHomePath)
    val toolsJar = File(jdkHomePath, "lib/tools.jar")
    if (toolsJar.exists()) {
      sdk.addRoot(toolsJar, JpsOrderRootType.COMPILED)
    }
    messages.info("'$jdkName' Java SDK set to $jdkHomePath")
  }

  /**
   * Code is copied from com.intellij.openapi.projectRoots.impl.JavaSdkImpl#findClasses(java.io.File, boolean)
   */
  fun readModulesFromReleaseFile(jbrBaseDir: Path): List<String> {
    val releaseFile = jbrBaseDir.resolve("release")
    if (!releaseFile.exists()) {
      throw IllegalStateException("JRE release file is missing: $releaseFile")
    }

    releaseFile.inputStream().use { stream ->
      val p = Properties()
      p.load(stream)
      val jbrBaseUrl = "${URLUtil.JRT_PROTOCOL}${URLUtil.SCHEME_SEPARATOR}${FileUtilRt.toSystemIndependentName(jbrBaseDir.toAbsolutePath().toString())}${URLUtil.JAR_SEPARATOR}"
      val modules = p.getProperty("MODULES") ?: return emptyList()
      return StringUtilRt.unquoteString(modules).split(' ').map { jbrBaseUrl + it }
    }
  }
}
