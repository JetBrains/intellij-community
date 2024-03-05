// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi

@Service
internal class IjentMainScopeHolder private constructor(myScope: CoroutineScope) {
  // don't dare close this scope
  @DelicateCoroutinesApi
  val scope = myScope

  companion object {
    fun getInstance(): IjentMainScopeHolder {
      return ApplicationManager.getApplication().service()
    }
  }
}