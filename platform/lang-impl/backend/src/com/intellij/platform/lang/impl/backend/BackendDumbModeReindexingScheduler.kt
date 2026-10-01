// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.impl.backend

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.DumbModeReindexingScheduler
import com.intellij.util.indexing.FileBasedIndexProjectHandler

internal class BackendDumbModeReindexingScheduler(private val project: Project) : DumbModeReindexingScheduler {
  override fun scheduleReindexingInDumbMode() {
    FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(project)
  }
}
