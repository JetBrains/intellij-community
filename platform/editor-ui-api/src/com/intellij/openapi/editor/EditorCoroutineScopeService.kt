// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditorCoroutineScopeService(private val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun defaultScope(project: Project?): CoroutineScope {
      return if (project == null || project.isDefault) {
        service<EditorCoroutineScopeService>().cs
      } else {
        project.service<EditorCoroutineScopeService>().cs
      }
    }
  }
}
