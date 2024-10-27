// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.components.Service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

@Service
internal class EelLocalApiService(private val scope: CoroutineScope) {
  fun scope(apiClass: KClass<*>): CoroutineScope = scope.childScope("EelExecApi for $apiClass")
}