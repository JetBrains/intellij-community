// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.project.Project

abstract class DiffVirtualFile(name: String) : DiffViewerVirtualFile(name) {

  abstract override fun createProcessor(project: Project): DiffRequestProcessor
}

abstract class DiffViewerVirtualFile(name: String) : DiffVirtualFileBase(name) {

  abstract fun createProcessor(project: Project): DiffEditorViewer
}
