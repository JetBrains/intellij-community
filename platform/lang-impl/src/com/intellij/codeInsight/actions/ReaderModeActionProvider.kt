// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.matchMode
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.GotItTooltip
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

private class ReaderModeActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project? = editor.project
    return if (project == null || project.isDefault) null
      else object : DefaultActionGroup(ReaderModeAction(editor), Separator.create()) {
        override fun update(e: AnActionEvent) {
          if (!Experiments.getInstance().isFeatureEnabled("editor.reader.mode")) {
            e.presentation.isEnabledAndVisible = false
          }
          else {
            if (project.isInitialized) {
              val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.virtualFile
              e.presentation.isEnabledAndVisible = matchMode(project, file, editor)
            }
            else {
              e.presentation.isEnabledAndVisible = false
            }
          }
        }
      }
  }

  private class ReaderModeAction(private val editor: Editor) : DumbAwareToggleAction(
    LangBundle.messagePointer("action.ReaderModeProvider.text"),
    LangBundle.messagePointer("action.ReaderModeProvider.description"),
    null), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
        override fun iconTextSpace() = JBUI.scale(2)

        override fun updateToolTipText() {
          val project = editor.project
          if (Registry.`is`("ide.helptooltip.enabled") && project != null) {
            HelpTooltip.dispose(this)
            HelpTooltip()
              .setTitle(myPresentation.description)
              .setDescription(LangBundle.message("action.ReaderModeProvider.description"))
              .setLink(LangBundle.message("action.ReaderModeProvider.link.configure"))
              { ShowSettingsUtil.getInstance().showSettingsDialog(project, ReaderModeConfigurable::class.java) }
              .installOn(this)
          }
          else {
            toolTipText = myPresentation.description
          }
        }

        override fun getInsets(): Insets = JBUI.insets(2)
        override fun getMargins(): Insets = if (myPresentation.icon == AllIcons.General.ReaderMode) JBInsets.emptyInsets()
        else JBUI.insetsRight(5)

        override fun updateUI() {
          super.updateUI()
          if (!SystemInfo.isWindows) {
            font = FontUIResource(font.deriveFont(font.style, font.size - JBUIScale.scale(2).toFloat()))
          }
        }
      }.also {
        it.foreground = JBColor.lazy { editor.colorsScheme.getColor(FOREGROUND) ?: FOREGROUND.defaultColor }
        if (!SystemInfo.isWindows) {
          it.font = FontUIResource(it.font.deriveFont(it.font.style, it.font.size - JBUIScale.scale(2).toFloat()))
        }

        editor.project?.let { p ->
          if (!ReaderModeSettings.getInstance(p).enabled || isNotificationSilentMode(p)) return@let

          val connection = p.messageBus.connect(p)
          val gotItTooltip = GotItTooltip("reader.mode.got.it", LangBundle.message("text.reader.mode.got.it.popup"), p)
                              .withHeader(LangBundle.message("title.reader.mode.got.it.popup"))

          if (gotItTooltip.canShow()) {
            connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
              override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                fileEditors.find { fe -> (fe is TextEditor) && editor == fe.editor }?.let { _ ->
                  gotItTooltip.setOnBalloonCreated { balloon ->
                    balloon.addListener(object: JBPopupListener {
                      override fun onClosed(event: LightweightWindowEvent) {
                        connection.disconnect()
                      }
                    })}.
                  show(it, GotItTooltip.BOTTOM_MIDDLE)
                }
              }
            })
          }
        }
      }

    override fun isSelected(e: AnActionEvent): Boolean {
      return true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return

      ReaderModeSettings.getInstance(project).enabled = !ReaderModeSettings.getInstance(project).enabled
      project.messageBus.syncPublisher(ReaderModeSettingsListener.TOPIC).modeChanged(project)
    }

    override fun update(e: AnActionEvent) {
      val project = editor.project ?: return
      val presentation = e.presentation

      if (!ReaderModeSettings.getInstance(project).enabled) {
        presentation.text = null
        presentation.icon = AllIcons.General.ReaderMode
        presentation.hoveredIcon = null
        presentation.description = LangBundle.message("action.ReaderModeProvider.text.enter")
      }
      else {
        presentation.text = LangBundle.message("action.ReaderModeProvider.text")
        presentation.icon = EmptyIcon.ICON_16
        presentation.hoveredIcon = AllIcons.Actions.CloseDarkGrey
        presentation.description = LangBundle.message("action.ReaderModeProvider.text.exit")
      }
    }
  }

  companion object {
    val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())
  }
}