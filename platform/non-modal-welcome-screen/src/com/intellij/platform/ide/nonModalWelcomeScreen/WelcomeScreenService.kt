// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class WelcomeScreenService(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): WelcomeScreenService = project.service()
    suspend fun getInstanceAsync(project: Project): WelcomeScreenService = project.serviceAsync()
  }
}
