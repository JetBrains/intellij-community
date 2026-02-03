// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.io.URLUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal object JdkUtils {
  fun defineJdk(global: JpsGlobal, jdkName: String, homeDir: Path) {
    JpsJavaExtensionService.getInstance().addJavaSdk(
      global,
      jdkName,
      FileUtilRt.toSystemIndependentName(homeDir.toCanonicalPath())
    )
    Span.current().addEvent("'$jdkName' JDK set", Attributes.of(AttributeKey.stringKey("jdkHomePath"), homeDir.toString()))
  }

  /**
   * Code is copied from [com.intellij.openapi.projectRoots.impl.JavaSdkImpl.findClasses]
   */
  fun readModulesFromReleaseFile(jbrBaseDir: Path): List<String> {
    val releaseFile = jbrBaseDir.resolve("release")
    check(Files.exists(releaseFile)) {
      "JRE release file is missing: $releaseFile"
    }

    Files.newInputStream(releaseFile).use { stream ->
      val p = Properties()
      p.load(stream)
      val jbrBaseUrl = "${URLUtil.JRT_PROTOCOL}${URLUtil.SCHEME_SEPARATOR}${
        FileUtilRt.toSystemIndependentName(jbrBaseDir.toAbsolutePath().toString())
      }${URLUtil.JAR_SEPARATOR}"
      val modules = p.getProperty("MODULES") ?: return emptyList()
      return StringUtilRt.unquoteString(modules).split(' ').map { jbrBaseUrl + it }
    }
  }
}
