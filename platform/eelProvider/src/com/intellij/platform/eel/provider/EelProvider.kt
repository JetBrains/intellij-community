// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.collections.firstNotNullOfOrNull

suspend fun Path.getEelApi(): EelApi =
  EP_NAME.extensionList.firstNotNullOfOrNull { it.getEelApi(this) } ?: LocalEelApi()

@ApiStatus.Internal
interface EelProvider {
  suspend fun getEelApi(path: Path): EelApi?
}

private val EP_NAME = ExtensionPointName<EelProvider>("com.intellij.eelProvider")

fun LocalEelApi(): EelApi = TODO()