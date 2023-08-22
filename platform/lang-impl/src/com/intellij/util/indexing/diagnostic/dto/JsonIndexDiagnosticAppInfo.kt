// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import java.time.ZoneOffset
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonIndexDiagnosticAppInfo(
  val build: String = "",
  val buildDate: JsonDateTime = JsonDateTime(),
  val productCode: String = "",
  val generated: JsonDateTime = JsonDateTime(),
  val os: String = "",
  val runtime: String = ""
) {
  companion object {
    fun create(): JsonIndexDiagnosticAppInfo {
      val appInfo = ApplicationInfo.getInstance()
      return JsonIndexDiagnosticAppInfo(
        build = appInfo.build.asStringWithoutProductCode(),
        buildDate = JsonDateTime(ZonedDateTime.ofInstant(appInfo.buildDate.toInstant(), appInfo.buildDate.timeZone.toZoneId())),
        productCode = appInfo.build.productCode,
        generated = JsonDateTime(ZonedDateTime.now(ZoneOffset.UTC)),
        os = SystemInfo.getOsNameAndVersion(),
        runtime = SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION
      )
    }
  }
}