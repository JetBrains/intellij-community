// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Suppress("RAW_RUN_BLOCKING")
@RequiresBackgroundThread
internal fun saveSettings(project: Project,
                          modalityState: ModalityState,
                          isUnitTestMode: Boolean) {
  runBlocking(Dispatchers.Default + modalityState.asContextElement()) {
    saveSettings(project)
    if (!isUnitTestMode) {
      saveSettings(ApplicationManager.getApplication())
    }
  }
}