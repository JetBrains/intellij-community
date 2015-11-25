// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestChainBase
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

class ChainDiffVirtualFile(private val chain: DiffRequestChain) : DiffVirtualFile() {
  override fun createProcessorAsync(project: Project): Builder {
    val proxyChain = if (chain is AsyncDiffRequestChain) {
      val requests = chain.loadRequestsInBackground()
      PreloadedProxyDiffRequestChain(chain, requests.list, requests.selectedIndex)
    }
    else {
      ProxyDiffRequestChain(chain)
    }

    return object : Builder {
      override fun build(): DiffRequestProcessor {
        return CacheDiffRequestChainProcessor(project, proxyChain)
      }
    }
  }

  private class ProxyDiffRequestChain(
    val delegate: DiffRequestChain
  ) : DiffRequestChainBase(delegate.index) {
    override fun getRequests(): List<DiffRequestProducer> = delegate.requests
    override fun <T> getUserData(key: Key<T>): T? = delegate.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = delegate.putUserData(key, value)
  }

  private class PreloadedProxyDiffRequestChain(
    val delegate: DiffRequestChain,
    val producers: List<DiffRequestProducer>,
    index: Int
  ) : DiffRequestChainBase(index) {
    override fun getRequests(): List<DiffRequestProducer> = producers
    override fun <T> getUserData(key: Key<T>): T? = delegate.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = delegate.putUserData(key, value)
  }
}
