// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.CustomCommandLaunchData


private val QODANA_ADDITIONAL_VM_OPTIONS = listOf(
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
  "-Deap.login.enabled=false"
)

private val QODANA_RIDER_ADDITIONAL_VM_OPTIONS = listOf(
  "-Dqodana.recommended.profile.resource=qodana-dotnet.recommended.yaml",
  "-Dqodana.starter.profile.resource=qodana-dotnet.starter.yaml",
  "-Dqodana.disable.default.fixes.strategy=true",
  "-Didea.class.before.app=com.jetbrains.rider.protocol.EarlyBackendStarter",
  "-Drider.collect.full.container.statistics=true",
  "-Drider.suppress.std.redirect=true"
)

private val QODANA_WEBSTORM_ADDITIONAL_VM_OPTIONS = listOf(
  "-Dqodana.recommended.profile.resource=qodana-js.recommended.yaml",
  "-Dqodana.starter.profile.resource=qodana-js.starter.yaml"
)



private fun getQodanaVmOptions(context: BuildContext, product: QodanaProduct): List<String> {
  val ideDependentOptions = when (product) {
    QodanaProduct.QDNET -> QODANA_RIDER_ADDITIONAL_VM_OPTIONS
    QodanaProduct.QDJS -> QODANA_WEBSTORM_ADDITIONAL_VM_OPTIONS
    else -> emptyList()
  }

  return QODANA_ADDITIONAL_VM_OPTIONS + ideDependentOptions + getQodanaAppInfoVmOptions(context, product)
}

private fun getQodanaAppInfoVmOptions(context: BuildContext, product: QodanaProduct): List<String> {
  return listOf(
    "-Dqodana.product.name=${product.productName}",
    "-Dqodana.build.number=${product.productCode}-${context.buildNumber}",
  )
}



internal fun generateQodanaLaunchData(
  ideContext: BuildContext,
  arch: JvmArchitecture,
  os: OsFamily,
  vmOptionsFilePath: (BuildContext) -> String
): CustomCommandLaunchData? {
  val qodanaProduct = QODANA_PRODUCTS[ideContext.productProperties.platformPrefix] ?: return null
  return CustomCommandLaunchData(
    commands = listOf("qodana"),
    vmOptionsFilePath = vmOptionsFilePath(ideContext),
    bootClassPathJarNames = ideContext.bootClassPathJarNames,
    additionalJvmArguments = ideContext.getAdditionalJvmArguments(os, arch) + getQodanaVmOptions(ideContext, qodanaProduct),
    mainClass = ideContext.ideMainClassName,
    dataDirectoryName = ideContext.systemSelector,
  )
}