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
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeys.ThreeSideDiffColors
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.UserDataHolder
import javax.swing.JComponent

class MutableDiffRequestChain : DiffRequestChainBase {
  private val producer = MyDiffRequestProducer()
  private val requestUserData: MutableMap<Key<*>, Any> = mutableMapOf()

  var content1: DiffContent
  var content2: DiffContent
  var baseContent: DiffContent? = null

  var windowTitle: String? = null
  var title1: String? = null
  var title2: String? = null
  var baseTitle: String? = null

  var baseColorMode: ThreeSideDiffColors = ThreeSideDiffColors.LEFT_TO_RIGHT

  constructor(content1: DiffContent, content2: DiffContent) {
    this.content1 = content1
    this.content2 = content2
    title1 = getTitleFor(content1)
    title2 = getTitleFor(content2)
  }

  constructor(content1: DiffContent, baseContent: DiffContent?, content2: DiffContent) {
    this.content1 = content1
    this.content2 = content2
    this.baseContent = baseContent
    title1 = getTitleFor(content1)
    title2 = getTitleFor(content2)
    baseTitle = if (baseContent != null) getTitleFor(baseContent) else null
  }

  fun <T : Any> putRequestUserData(key: Key<T>, value: T) {
    requestUserData.put(key, value)
  }

  override fun getRequests(): List<DiffRequestProducer> = listOf(producer)

  private inner class MyDiffRequestProducer : DiffRequestProducer {
    override fun getName(): String {
      return DiffBundle.message("diff.files.generic.request.title")
    }

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
      val request = if (baseContent != null) {
        SimpleDiffRequest(windowTitle, content1, baseContent!!, content2, title1, baseTitle, title2).also {
          putUserData(DiffUserDataKeys.THREESIDE_DIFF_COLORS_MODE, baseColorMode)
        }
      }
      else {
        SimpleDiffRequest(windowTitle, content1, content2, title1, title2)
      }
      request.putUserData(CHAIN_KEY, this@MutableDiffRequestChain)
      requestUserData.forEach { key, value ->
        @Suppress("UNCHECKED_CAST")
        request.putUserData(key as Key<Any>, value)
      }
      return request
    }
  }

  companion object {
    private val CHAIN_KEY = Key.create<MutableDiffRequestChain>("Diff.MutableDiffRequestChain")

    fun createHelper(context: DiffContext, request: DiffRequest): Helper? {
      if (context !is DiffContextEx) return null
      val chain = request.getUserData(CHAIN_KEY) ?: return null
      return Helper(chain, context)
    }

    fun createHelper(dataContext: DataContext): Helper? {
      val context = dataContext.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
      val request = dataContext.getData(DiffDataKeys.DIFF_REQUEST) ?: return null
      return createHelper(context, request)
    }
  }

  data class Helper(val chain: MutableDiffRequestChain, val context: DiffContextEx) {
    fun setContent(newContent: DiffContent, side: Side) {
      setContent(newContent, getTitleFor(newContent), side)
    }

    fun setContent(newContent: DiffContent, side: ThreeSide) {
      setContent(newContent, getTitleFor(newContent), side)
    }

    fun setContent(newContent: DiffContent, title: String?, side: Side) {
      setContent(newContent, title, side.selectNotNull(ThreeSide.LEFT, ThreeSide.RIGHT))
    }

    fun setContent(newContent: DiffContent, title: String?, side: ThreeSide) {
      when (side) {
        ThreeSide.LEFT -> {
          chain.content1 = newContent
          chain.title1 = title
        }
        ThreeSide.RIGHT -> {
          chain.content2 = newContent
          chain.title2 = title
        }
        ThreeSide.BASE -> {
          chain.baseContent = newContent
          chain.baseTitle = title
        }
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

    e.presentation.isEnabledAndVisible = helper.chain.baseContent == null
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

internal class SwapThreeWayColorModeAction : ComboBoxAction() {
  override fun update(e: AnActionEvent) {
    val presentation = e.presentation

    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    if (helper != null) {
      presentation.text = getText(helper.chain.baseColorMode)
      presentation.isEnabledAndVisible = true && helper.chain.baseContent != null
    }
    else {
      presentation.isEnabledAndVisible = false
    }
  }

  override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
    return DefaultActionGroup(ThreeSideDiffColors.values().map { MyAction(getText(it), it) })
  }

  private fun getText(option: ThreeSideDiffColors): @NlsActions.ActionText String {
    return when (option) {
      ThreeSideDiffColors.MERGE_CONFLICT -> DiffBundle.message("option.three.side.color.policy.merge.conflict")
      ThreeSideDiffColors.MERGE_RESULT -> DiffBundle.message("option.three.side.color.policy.merge.resolved")
      ThreeSideDiffColors.LEFT_TO_RIGHT -> DiffBundle.message("option.three.side.color.policy.left.to.right")
    }
  }

  private inner class MyAction(text: @NlsActions.ActionText String, val option: ThreeSideDiffColors) : DumbAwareAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
      val helper = MutableDiffRequestChain.createHelper(e.dataContext) ?: return
      helper.chain.baseColorMode = option
      helper.fireRequestUpdated()
    }
  }
}

private fun getTitleFor(content: DiffContent) =
  if (content is FileContent) DiffRequestFactory.getInstance().getContentTitle(content.file) else null
