// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.CoroutineScope

@Service
internal class IjentApplicationScope private constructor(scope: CoroutineScope) : CoroutineScope by scope {
  companion object {
    fun instance(): IjentApplicationScope = ApplicationManager.getApplication().service()
    suspend fun instanceAsync(): IjentApplicationScope = serviceAsync()
  }
}