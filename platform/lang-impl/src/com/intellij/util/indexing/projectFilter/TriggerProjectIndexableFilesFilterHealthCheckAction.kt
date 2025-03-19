// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class TriggerProjectIndexableFilesFilterHealthCheckAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.getService(ProjectIndexableFilesFilterHealthCheck::class.java)?.launchHealthCheck()
  }
}