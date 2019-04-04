// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.actions.impl.GoToChangePopupBuilder
import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestChainBase
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.Consumer

class ChainDiffVirtualFile(private val chain: DiffRequestChain) : DiffVirtualFile() {
  override fun createProcessorAsync(project: Project): Builder {
    val edtCallback = (chain as? AsyncDiffRequestChain)?.forceLoadRequests()

    return Builder.create {
      edtCallback?.run()

      val proxyChain = ProxyDiffRequestChain(chain)
      CacheDiffRequestChainProcessor(project, proxyChain)
    }
  }

  private class ProxyDiffRequestChain(
    val delegate: DiffRequestChain
  ) : DiffRequestChainBase(delegate.index), GoToChangePopupBuilder.Chain {
    override fun getRequests(): List<DiffRequestProducer> = delegate.requests
    override fun <T> getUserData(key: Key<T>): T? = delegate.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = delegate.putUserData(key, value)
    override fun createGoToChangeAction(onSelected: Consumer<in Int>): AnAction? =
      (delegate as? GoToChangePopupBuilder.Chain)?.createGoToChangeAction(onSelected)
  }
}
