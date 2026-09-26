// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelWindowsApi
import com.intellij.platform.eel.LocalEelApi
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

@ApiStatus.Experimental
interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi

@ApiStatus.Experimental
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

/**
 * SPI for providing the local EEL API. Loaded via [ServiceLoader] from `eel-impl`.
 */
@ApiStatus.Internal
fun interface LocalEelApiProvider {
  fun getLocalEelApi(): LocalEelApi
}

@get:ApiStatus.Experimental
val localEel: LocalEelApi by lazy {
  ServiceLoader.load(LocalEelApiProvider::class.java, LocalEelApiProvider::class.java.classLoader)
    .firstOrNull()?.getLocalEelApi()
  ?: error("No LocalEelApiProvider found on classpath. Ensure intellij.platform.eel.impl is available.")
}
