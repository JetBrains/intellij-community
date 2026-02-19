// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface AutoScrollToSourceTaskManager {

  companion object {

    @JvmStatic
    fun getInstance(): AutoScrollToSourceTaskManager = ApplicationManager.getApplication().service()
  }

  @RequiresEdt
  fun scheduleScrollToSource(
    handler: AutoScrollToSourceHandler,
    dataContext: DataContext,
  )
}