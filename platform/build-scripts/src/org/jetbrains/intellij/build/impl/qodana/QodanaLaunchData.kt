// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData


internal fun generateQodanaLaunchData(
  ideContext: BuildContext,
  arch: JvmArchitecture,
  os: OsFamily,
): CustomCommandLaunchData? {
  val qodanaProductProperties = ideContext.productProperties.qodanaProductProperties ?: return null
  val vmOptions = ideContext.getAdditionalJvmArguments(os, arch) + qodanaProductProperties.getAdditionalVmOptions(ideContext)
  return CustomCommandLaunchData(
    commands = listOf("qodana"),
    additionalJvmArguments = vmOptions
  )
}