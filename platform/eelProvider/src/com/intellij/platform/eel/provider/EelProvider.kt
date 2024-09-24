// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.impl.local.LocalPosixEelApiImpl
import com.intellij.platform.eel.impl.local.LocalWindowsEelApiImpl
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

suspend fun Project.getEelApi(): EelApi {
  val path = basePath ?: throw IllegalStateException("Cannot find base dir for project")
  return Path.of(path).getEelApi()
}

suspend fun Path.getEelApi(): EelApi =
  EP_NAME.extensionList.firstNotNullOfOrNull { it.getEelApi(this) }
  ?: if (SystemInfo.isWindows) LocalWindowsEelApiImpl() else LocalPosixEelApiImpl()

@ApiStatus.Internal
interface EelProvider {
  suspend fun getEelApi(path: Path): EelApi?
}

private val EP_NAME = ExtensionPointName<EelProvider>("com.intellij.eelProvider")