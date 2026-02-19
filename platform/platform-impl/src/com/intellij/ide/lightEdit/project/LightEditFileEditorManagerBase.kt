// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class LightEditFileEditorManagerBase(
  project: Project,
  coroutineScope: CoroutineScope
) : FileEditorManagerImpl(project, coroutineScope) {

  @RequiresEdt
  suspend fun internalInit() {
    super.init()
  }
}