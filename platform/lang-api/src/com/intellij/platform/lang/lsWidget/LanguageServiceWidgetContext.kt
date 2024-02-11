// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class LanguageServiceWidgetContext(
  val project: Project,
  val currentFile: VirtualFile?,
  val updateWidget: () -> Unit,
  val widgetDisposable: Disposable,
)
