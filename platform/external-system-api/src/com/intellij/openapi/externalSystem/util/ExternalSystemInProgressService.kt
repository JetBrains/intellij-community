// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.observable.AbstractInProgressService
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class ExternalSystemInProgressService(scope: CoroutineScope) : AbstractInProgressService(scope) {
  @Volatile
  private var isUnlinkedActivityStarted: Boolean = false
  @Volatile
  private var isExternalProjectActivityStarted: Boolean = false

  fun unlinkedActivityStarted() {
    isUnlinkedActivityStarted = true
  }

  fun externalSystemActivityStarted() {
    isExternalProjectActivityStarted = true
  }

  private fun isUnlinkedActivityPending() : Boolean{
    return !ApplicationManager.getApplication().isUnitTestMode && !isUnlinkedActivityStarted
  }

  override fun isInProgress(): Boolean {
    return super.isInProgress() ||
           isUnlinkedActivityPending() ||
           !isExternalProjectActivityStarted
  }
}