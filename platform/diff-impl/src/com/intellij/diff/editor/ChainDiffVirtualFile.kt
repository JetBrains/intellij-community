// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.openapi.project.Project

class ChainDiffVirtualFile(private val chain: DiffRequestChain) : DiffVirtualFile() {
  override fun createProcessorAsync(project: Project): Builder {
    return Builder.create {
      CacheDiffRequestChainProcessor(project, chain)
    }
  }
}
