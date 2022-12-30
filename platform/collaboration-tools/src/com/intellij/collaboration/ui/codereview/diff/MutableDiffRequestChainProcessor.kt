// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import kotlin.properties.Delegates

abstract class MutableDiffRequestChainProcessor(project: Project, chain: DiffRequestChain?) : CacheDiffRequestProcessor.Simple(project) {

  private val asyncChangeListener = AsyncDiffRequestChain.Listener {
    dropCaches()
    currentIndex = (this.chain?.index ?: 0)
    updateRequest(true)
  }

  var chain: DiffRequestChain? by Delegates.observable(null) { _, oldValue, newValue ->
    if (oldValue is AsyncDiffRequestChain) {
      oldValue.onAssigned(false)
      oldValue.removeListener(asyncChangeListener)
    }

    if (newValue is AsyncDiffRequestChain) {
      newValue.onAssigned(true)
      // listener should be added after `onAssigned` call to avoid notification about synchronously loaded requests
      newValue.addListener(asyncChangeListener, this)
    }
    currentIndex = newValue?.index ?: 0
    updateRequest()
  }
  private var currentIndex: Int = 0

  init {
    this.chain = chain
  }

  override fun onDispose() {
    val chain = chain
    if (chain is AsyncDiffRequestChain) chain.onAssigned(false)

    super.onDispose()
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer? {
    val requests = chain?.requests ?: return null
    return if (currentIndex < 0 || currentIndex >= requests.size) null else requests[currentIndex]
  }

  override fun hasNextChange(fromUpdate: Boolean): Boolean {
    val chain = chain ?: return false
    return currentIndex < chain.requests.lastIndex
  }

  override fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val chain = chain ?: return false
    return currentIndex > 0 && chain.requests.size > 1
  }

  override fun goToNextChange(fromDifferences: Boolean) {
    goToNextChangeImpl(fromDifferences) {
      currentIndex += 1
      selectCurrentChange()
    }
  }

  override fun goToPrevChange(fromDifferences: Boolean) {
    goToPrevChangeImpl(fromDifferences) {
      currentIndex -= 1
      selectCurrentChange()
    }
  }

  override fun isNavigationEnabled(): Boolean {
    val chain = chain ?: return false
    return chain.requests.size > 1
  }

  override fun createGoToChangeAction(): AnAction? {
    return MyGoToChangePopupAction()
  }

  abstract fun selectFilePath(filePath: FilePath)

  private fun selectCurrentChange() {
    val producer = currentRequestProvider as? ChangeDiffRequestChain.Producer ?: return
    selectFilePath(producer.filePath)
  }

  private inner class MyGoToChangePopupAction : PresentableGoToChangePopupAction.Default<ChangeDiffRequestChain.Producer>() {
    override fun getChanges(): ListSelection<out ChangeDiffRequestChain.Producer> {
      val requests = chain?.requests ?: return ListSelection.empty()
      val list = ListSelection.createAt(requests, currentIndex)
      return list.map { it as? ChangeDiffRequestChain.Producer }
    }

    override fun onSelected(change: ChangeDiffRequestChain.Producer) {
      selectFilePath(change.filePath)
    }
  }
}
