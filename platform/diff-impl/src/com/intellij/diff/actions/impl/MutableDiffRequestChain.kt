// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffContextEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.chains.DiffRequestChainBase
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.Side
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder

class MutableDiffRequestChain(var content1: DiffContent, var content2: DiffContent) : DiffRequestChainBase() {
  private val requestUserData: MutableMap<Key<*>, Any> = mutableMapOf()
  var windowTitle: String? = null
  var title1: String? = getTitleFor(content1)
  var title2: String? = getTitleFor(content2)

  fun <T : Any> putRequestUserData(key: Key<T>, value: T) {
    requestUserData.put(key, value)
  }

  override fun getRequests(): List<DiffRequestProducer> {
    return listOf(object : DiffRequestProducer {
      override fun getName(): String {
        return "Change"
      }

      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
        val request = MutableChainDiffRequest(this@MutableDiffRequestChain)
        requestUserData.forEach { key, value ->
          @Suppress("UNCHECKED_CAST")
          request.putUserData(key as Key<Any>, value)
        }
        return request
      }
    })
  }

  companion object {
    fun createHelper(context: DiffContext, request: DiffRequest): Helper? {
      if (context !is DiffContextEx || request !is MutableChainDiffRequest) return null
      return Helper(request.chain, context)
    }

    fun createHelper(dataContext: DataContext): Helper? {
      val context = dataContext.getData(DiffDataKeys.DIFF_CONTEXT) as? DiffContextEx ?: return null
      val request = dataContext.getData(DiffDataKeys.DIFF_REQUEST) as? MutableChainDiffRequest ?: return null
      return Helper(request.chain, context)
    }
  }

  data class Helper(val chain: MutableDiffRequestChain, val context: DiffContextEx) {
    fun setContent(newContent: DiffContent, side: Side) {
      setContent(newContent, getTitleFor(newContent), side)
    }

    fun setContent(newContent: DiffContent, title: String?, side: Side) {
      if (side.isLeft) {
        chain.content1 = newContent
        chain.title1 = title
      }
      else {
        chain.content2 = newContent
        chain.title2 = title
      }
    }

    fun fireRequestUpdated() {
      chain.requestUserData.clear()
      context.reloadDiffRequest()
    }
  }
}

internal class SwapDiffSidesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    if (helper == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)!!

    val oldContent1 = helper.chain.content1
    val oldContent2 = helper.chain.content2
    val oldTitle1 = helper.chain.title1
    val oldTitle2 = helper.chain.title2

    helper.setContent(oldContent1, oldTitle1, Side.RIGHT)
    helper.setContent(oldContent2, oldTitle2, Side.LEFT)
    helper.fireRequestUpdated()
  }
}

private fun getTitleFor(content: DiffContent) =
  if (content is FileContent) DiffRequestFactory.getInstance().getContentTitle(content.file) else null

private class MutableChainDiffRequest(val chain: MutableDiffRequestChain)
  : SimpleDiffRequest(chain.windowTitle, chain.content1, chain.content2, chain.title1, chain.title2)
