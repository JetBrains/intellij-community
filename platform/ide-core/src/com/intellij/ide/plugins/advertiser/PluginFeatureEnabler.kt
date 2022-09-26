// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PluginFeatureEnabler {

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  suspend fun enableSuggested(): Boolean

  fun scheduleEnableSuggested()

  companion object {

    @JvmStatic
    fun getInstance(project: Project): PluginFeatureEnabler = project.service()
  }
}