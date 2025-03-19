// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ProjectBatchFileChangeListener(private val project: Project) : BatchFileChangeListener {
  open fun batchChangeStarted(activityName: String?) {}

  open fun batchChangeCompleted() {}

  final override fun batchChangeStarted(project: Project, activityName: String?) {
    if (project === this.project) {
      batchChangeStarted(activityName)
    }
  }

  final override fun batchChangeCompleted(project: Project) {
    if (project === this.project) {
      batchChangeCompleted()
    }
  }
}