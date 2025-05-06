// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageInfoAdapter

interface FindExecutor {
  companion object {
    fun getInstance(): FindExecutor {
      return ApplicationManager.getApplication().getService(FindExecutor::class.java)
    }
  }

  fun findUsages(
    project: Project,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  )
}