// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import org.jetbrains.intellij.build.BuildContext

private val COMMON_ADDITIONAL_VM_OPTIONS = listOf(
  "-Dqodana.application=true",
  "-Dintellij.platform.load.app.info.from.resources=true",
  "-Dfus.internal.reduce.initial.delay=true",
  "-Didea.headless.statistics.max.files.to.send=5000",
  "-Djava.awt.headless=true",
  "-Didea.qodana.thirdpartyplugins.accept=true",
  "-Dide.warmup.use.predicates=false",
  "-Dvcs.log.index.enable=false",
  "-Didea.job.launcher.without.timeout=true",
  "-Dscanning.in.smart.mode=false",
  "-Deap.login.enabled=false",
  "-Dsdk.download.consent=true"
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
  fun getAdditionalVmOptions(context: BuildContext): List<String> {
    val appInfoOptions = listOf(
      "-Dqodana.product.name=$productName",
      "-Dqodana.build.number=$productCode-${context.buildNumber}",
      "-Dqodana.eap=${isEap(productCode)}",
    )
    return COMMON_ADDITIONAL_VM_OPTIONS + customVmOptions + appInfoOptions
  }
}

private fun isEap(productCode: String): Boolean = when (productCode) {
  "QDCPP", "QDRST", "QDRUBY" -> true
  else -> IS_EAP
}