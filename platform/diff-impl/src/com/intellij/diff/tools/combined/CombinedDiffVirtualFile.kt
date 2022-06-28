// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.project.Project

abstract class CombinedDiffVirtualFile<P : CombinedDiffRequestProducer>(protected val requestProducer: P) :
  DiffVirtualFileBase(requestProducer.name) {

  abstract fun createProcessor(project: Project): CombinedDiffRequestProcessor

  override fun getPath(): String  = name
}
