// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.matchMode
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.plaf.FontUIResource

internal class ReaderModeActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project? = editor.project
    return if (project == null || project.isDefault) null
    else object : DefaultActionGroup(ReaderModeAction(editor), Separator.create()), ActionRemoteBehaviorSpecification.Frontend {

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false
        if (Experiments.getInstance().isFeatureEnabled("editor.reader.mode")) {
          val p = e.project ?: return
          if (p.isInitialized) {
            val textEditor = e.getData(CommonDataKeys.EDITOR) ?: return
            val file = PsiDocumentManager.getInstance(p).getPsiFile(textEditor.document)?.virtualFile
            e.presentation.isEnabledAndVisible = file != null && matchMode(project = p, file = file, editor = textEditor)
          }
        }
      }
    }
  }

  private class ReaderModeAction(private val editor: Editor) : DumbAwareToggleAction(
    LangBundle.messagePointer("action.ReaderModeProvider.text"),
    LangBundle.messagePointer("action.ReaderModeProvider.description"),
    null), CustomComponentAction {

    private val FOREGROUND = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val component = object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
        override fun iconTextSpace() = JBUI.scale(2)

        override fun updateToolTipText() {
          val project = editor.project
          if (project != null && UISettings.isIdeHelpTooltipEnabled()) {
            HelpTooltip.dispose(this)
            HelpTooltip()
              .setTitle(myPresentation.description)
              .setDescription(LangBundle.message("action.ReaderModeProvider.description"))
              .setLink(LangBundle.message("action.ReaderModeProvider.link.configure")) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                  project,
                  /*predicate =*/{ it: Configurable? ->
                    if (it is ConfigurableWrapper) {
                      val unwrapped = it.configurable
                      unwrapped is BoundSearchableConfigurable && unwrapped.id == "editor.reader.mode"
                    } else false
                  },
                  /*additionalConfiguration =*/null
                )
              }
              .installOn(this)
          }
          else {
            toolTipText = myPresentation.description
          }
        }

        override fun getInsets(): Insets = JBUI.insets(2)

        override fun getMargins(): Insets = JBInsets.addInsets(
          super.getMargins(),
          if (myPresentation.icon == AllIcons.General.ReaderMode) JBInsets.emptyInsets() else JBUI.insetsRight(5)
        )

        override fun updateUI() {
          super.updateUI()
          if (!SystemInfo.isWindows) {
            font = FontUIResource(font.deriveFont(font.style, font.size - JBUIScale.scale(2).toFloat()))
          }
        }
      }

      component.foreground = JBColor.lazy {
        editor.colorsScheme.getColor(FOREGROUND) ?: FOREGROUND.defaultColor ?: UIUtil.getInactiveTextColor()
      }
      if (!SystemInfo.isWindows) {
        component.font = FontUIResource(component.font.deriveFont(component.font.style, component.font.size - JBUIScale.scale(2).toFloat()))
      }

      return component
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return

      val newEnabled = !ReaderModeSettings.getInstance(project).enabled

      ReaderModeSettings.getInstance(project).enabled = newEnabled
      project.messageBus.syncPublisher(ReaderModeSettingsListener.TOPIC).modeChanged(project)

      ReaderModeStatsCollector.readerModeSwitched(newEnabled)
    }

    override fun update(e: AnActionEvent) {
      val project = e.project ?: return
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
}