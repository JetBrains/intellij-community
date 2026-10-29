// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// list contains non-overridable parameters!
// user won't be able to change them by using --property in qodana-cli.
// when adding an overridable parameter (i.e. a default value), add it in qodana-cli instead.
// if applicable, keep tests equivalent org.jetbrains.qodana.tests.utils.QodanaVmOptions in sync
private val COMMON_ADDITIONAL_VM_OPTIONS = listOf(
  "-Dqodana.application=true",
  "-Dfus.internal.reduce.initial.delay=true",
  "-Didea.headless.statistics.max.files.to.send=5000",
  "-Djava.awt.headless=true",
  "-Didea.qodana.thirdpartyplugins.accept=true",
  "-Dide.warmup.use.predicates=false",
  "-Dvcs.log.index.enable=false",
  "-Didea.job.launcher.without.timeout=true",
  "-Dscanning.in.smart.mode=false",
  "-Deap.login.enabled=false",
  "-Dsdk.download.consent=true",
  "-Dide.activity.tracking.enable.debug=true",
  "-Deslint.service.expiration.timeout.ms=5000",
  "-Djetbrains.security.package-checker.requestTimeoutSeconds=15",
  "-Dphp.additional.library.manager.new.updater=true",
  "-Dide.region.url.mapping.expiration.timeout=7200",
  "-Dexternal.system.auto.import.headless.async=true",
  "-Dmaven.default.headless.import=true",
  "-Dide.do.not.check.ls.on.startup=true",
)

private const val IS_EAP = true

/**
 * Represents a set of properties specific to the Qodana product.
 *
 * @property productCode Qodana product code - QDJVM, QDPY, etc... Will be set in runtime instead of bundled product code.
 * @property productName Qodana product code - "Qodana for JVM", etc... Will be set in runtime instead of bundled product name.
 * @property customVmOptions Distribution-specific vm options required for Qodana application.
 */
class QodanaProductProperties(val productCode: String, val productName: String, private val customVmOptions: List<String> = emptyList()) {
  fun getAdditionalVmOptions(buildNumber: String, buildDateInSeconds: Long?): List<String> {
    val appInfoOptions = buildList {
      add("-Dqodana.product.name=$productName")
      add("-Dqodana.build.number=$productCode-$buildNumber")
      if (buildDateInSeconds != null) {
        val buildDate = ZonedDateTime.ofInstant(
          Instant.ofEpochSecond(buildDateInSeconds),
          ZoneOffset.UTC
        ).format(DateTimeFormatter.ofPattern("uuuuMMddHHmm"))
        add("-Dqodana.build.date=$buildDate")
      }
      add("-Dqodana.eap=${isEap(productCode)}")
    }
    return COMMON_ADDITIONAL_VM_OPTIONS + customVmOptions + appInfoOptions
  }
}

private fun isEap(productCode: String): Boolean = when (productCode) {
  "QDRST", "QDRUBY", "QDPOLY" -> true
  else -> IS_EAP
}