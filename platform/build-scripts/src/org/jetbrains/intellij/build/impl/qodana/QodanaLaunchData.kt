// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

internal fun generateQodanaLaunchData(ideContext: BuildContext, arch: JvmArchitecture, os: OsFamily): CustomCommandLaunchData? {
  val qodanaProductProperties = ideContext.productProperties.qodanaProductProperties ?: return null
  val vmOptions = ideContext.getAdditionalJvmArguments(os, arch, isQodana = false) + qodanaProductProperties.getAdditionalVmOptions(ideContext)
  return CustomCommandLaunchData(
    commands = listOf("qodana"),
    additionalJvmArguments = vmOptions
  )
}
