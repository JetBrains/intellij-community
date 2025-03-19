// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffContextEx
import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.impl.DiffEditorTitleDetails
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeys.ThreeSideDiffColors
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent
import kotlin.collections.List
import kotlin.collections.MutableMap
import kotlin.collections.buildList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableMapOf

class MutableDiffRequestChain @JvmOverloads constructor(
  var content1: DiffContent,
  var baseContent: DiffContent?,
  var content2: DiffContent,
  private val project: Project? = null,
) : UserDataHolderBase(), DiffRequestChain {

  private val producer = MyDiffRequestProducer()
  private val requestUserData: MutableMap<Key<*>, Any> = mutableMapOf()

  var windowTitle: String? = null
  var title1: String? = null
  var title2: String? = null
  var baseTitle: String? = null

  var baseColorMode: ThreeSideDiffColors = ThreeSideDiffColors.LEFT_TO_RIGHT

  @JvmOverloads
  constructor(content1: DiffContent, content2: DiffContent, project: Project? = null) : this(content1, null, content2, project)

  fun <T : Any> putRequestUserData(key: Key<T>, value: T) {
    requestUserData.put(key, value)
  }

  override fun getRequests(): List<DiffRequestProducer> = listOf(producer)
  override fun getIndex(): Int = 0

  private inner class MyDiffRequestProducer : DiffRequestProducer {
    override fun getName(): String {
      return DiffBundle.message("diff.files.generic.request.title")
    }

    override fun getContentType(): FileType? = content1.contentType ?: content2.contentType

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
      requestUserData.forEach { (key, value) ->
        @Suppress("UNCHECKED_CAST")
        request.putUserData(key as Key<Any>, value)
      }

      return DiffUtil.addTitleCustomizers(request, buildList {
        add(getTitleCustomizer(content1, title1))
        if (baseContent != null) {
          add(getTitleCustomizer(baseContent, baseTitle))
        }
        add(getTitleCustomizer(content2, title2))
      })
    }

    private fun getTitleCustomizer(content: DiffContent?, customTitle: @NlsSafe String?): DiffEditorTitleCustomizer = when {
      customTitle != null -> DiffEditorTitleDetails.createFromTitle(customTitle)
      content is FileContent -> DiffEditorTitleDetails.create(project, LocalFilePath(content.file.path, content.file.isDirectory), null)
      else -> DiffEditorTitleDetails.EMPTY
    }.getCustomizer()
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
      setContent(newContent, null, side)
    }

    fun setContent(newContent: DiffContent, side: ThreeSide) {
      setContent(newContent, null, side)
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
      chain.windowTitle = null
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

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
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

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
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

private fun getDisplayPath(guessedProjectDir: VirtualFile?, file: VirtualFile): String {
  if (guessedProjectDir == null) return FileUtil.getLocationRelativeToUserHome(file.presentableUrl)

  val filePath = file.toNioPath()
  val projectDirPath = guessedProjectDir.toNioPath()
  return if (filePath.startsWith(projectDirPath)) projectDirPath.relativize(filePath).toString() else FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
}
