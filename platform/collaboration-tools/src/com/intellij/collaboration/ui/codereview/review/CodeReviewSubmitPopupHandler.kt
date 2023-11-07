// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.review

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.popup.awaitClose
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.showAbove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

abstract class CodeReviewSubmitPopupHandler<VM : CodeReviewSubmitViewModel> {
  suspend fun show(vm: VM, parentComponent: Component, above: Boolean = false) {
    withContext(Dispatchers.Main) {
      val container = createPopupComponent(vm)
      val popup = createPopup(container)

      if (above) {
        popup.showAbove(parentComponent)
      }
      else {
        popup.showUnderneathOf(parentComponent)
      }
      popup.awaitClose()
    }
  }

  suspend fun show(vm: VM, project: Project) {
    withContext(Dispatchers.Main) {
      val container = createPopupComponent(vm)
      val popup = createPopup(container)

      popup.showCenteredInCurrentWindow(project)
      popup.awaitClose()
    }
  }

  protected companion object {
    // gap 12 minus button borders (3x2)
    const val ACTIONS_GAP: Int = 6
    const val TITLE_ACTIONS_GAP: Int = 5
  }

  protected open fun createTitleActionsComponentIn(cs: CoroutineScope, vm: VM): JComponent {
    return InlineIconButton(
      icon = AllIcons.Actions.Close,
      hoveredIcon = AllIcons.Actions.CloseHovered
    ).apply {
      border = JBUI.Borders.empty(5)
      actionListener = ActionListener { vm.cancel() }
    }
  }

  protected abstract fun CoroutineScope.createActionsComponent(vm: VM): JComponent

  private fun CoroutineScope.createPopupComponent(vm: VM): ComponentContainer {
    val cs = this
    return object : ComponentContainer {
      private val editor = createEditor(vm.text)

      private val panel = createPanel()

      override fun getComponent(): JComponent = panel

      override fun getPreferredFocusableComponent(): EditorTextField = editor

      override fun dispose() {}

      private fun createPanel(): JComponent {
        val titleLabel = JLabel(CollaborationToolsBundle.message("review.submit.review.title")).apply {
          font = font.deriveFont(font.style or Font.BOLD)
        }
        val titleActions = createTitleActionsComponentIn(cs, vm)
        val titlePanel = JPanel(HorizontalLayout(TITLE_ACTIONS_GAP)).apply {
          isOpaque = false
          add(titleLabel, HorizontalLayout.LEFT)
          bindChildIn(cs, vm.draftCommentsCount, HorizontalLayout.LEFT, 1) {
            if (it <= 0) null else JLabel(CollaborationToolsBundle.message("review.pending.comments.count", it))
          }
          add(titleActions, HorizontalLayout.RIGHT)
        }

        val errorPanel = SimpleHtmlPane().apply {
          bindTextIn(cs, vm.error.map { CollaborationToolsBundle.message("review.comment.placeholder") + "\n" + it?.localizedMessage })
          bindVisibilityIn(cs, vm.error.map { it != null })
        }

        val buttonsPanel = createActionsComponent(vm)

        return JPanel(MigLayout(LC().insets("12").fill().flowY().noGrid().hideMode(3))).apply {
          background = JBUI.CurrentTheme.Popup.BACKGROUND
          preferredSize = JBDimension(500, 200)

          add(titlePanel, CC().growX())
          add(editor, CC().growX().growY())
          add(errorPanel, CC().growY().growPrioY(0))
          add(buttonsPanel, CC())
        }
      }

      private fun createEditor(text: MutableStateFlow<String>): EditorTextField =
        EditorTextField(text.value, null, FileTypes.PLAIN_TEXT).apply {
          setOneLineMode(false)
          setPlaceholder(CollaborationToolsBundle.message("review.comment.placeholder"))
          addSettingsProvider {
            it.settings.isUseSoftWraps = true
            it.setVerticalScrollbarVisible(true)
            it.scrollPane.viewportBorder = JBUI.Borders.emptyLeft(4)
            it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
          }
          document.bindTextIn(cs, text)
        }
    }
  }

  private fun createPopup(container: ComponentContainer): JBPopup = JBPopupFactory.getInstance()
    // popup requires a properly focusable component, will not look under a panel
    .createComponentPopupBuilder(container.component, container.preferredFocusableComponent)
    .setFocusable(true)
    .setRequestFocus(true)
    .setResizable(true)
    .createPopup()
}