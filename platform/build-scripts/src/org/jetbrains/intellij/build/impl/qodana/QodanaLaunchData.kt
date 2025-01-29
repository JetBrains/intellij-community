// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS

internal fun generateQodanaLaunchData(ideContext: BuildContext, arch: JvmArchitecture, os: OsFamily): CustomCommandLaunchData? {
  val qodanaProductProperties = ideContext.productProperties.qodanaProductProperties ?: return null
  val vmOptions = ideContext.getAdditionalJvmArguments(os, arch, isQodana = true) + qodanaProductProperties.getAdditionalVmOptions(ideContext)
  return CustomCommandLaunchData(
    commands = listOf("qodana"),
    bootClassPathJarNames = ideContext.bootClassPathJarNames + PLATFORM_CORE_NIO_FS,
    additionalJvmArguments = vmOptions
  )
}
