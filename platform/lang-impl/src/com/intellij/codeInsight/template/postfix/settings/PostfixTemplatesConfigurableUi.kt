// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.AsyncInitPlaceholder
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.AnActionButtonUpdater
import com.intellij.ui.JBSplitter
import com.intellij.ui.LayeredIcon
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.util.ui.update.UpdateQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

internal class PostfixTemplatesConfigurableUi : Disposable {

  lateinit var completionEnabledCheckbox: JBCheckBox
  lateinit var postfixTemplatesEnabled: JBCheckBox
  lateinit var postfixTemplatesGroupCompletion: JBCheckBox
  lateinit var shortcutComboBox: ComboBox<Char>

  @JvmField
  val checkboxTree: PostfixTemplatesCheckboxTree = object : PostfixTemplatesCheckboxTree() {
    override fun selectionChanged() {
      selectionChanges.queue(Unit)
    }
  }

  @JvmField
  val innerPostfixDescriptionPanel = PostfixDescriptionPanel()

  private val selectionChanges: UpdateQueue<Unit>

  @JvmField
  val panel: DialogPanel = panel {
    row {
      postfixTemplatesEnabled = checkBox(CodeInsightBundle.message("postfix.completion.option.enabled"))
        .component
    }
    indent {
      row {
        postfixTemplatesGroupCompletion = checkBox(CodeInsightBundle.message("postfix.completion.option.group.enabled"))
          .component
      }
    }.visible(GroupedCompletionContributor.isGroupEnabledInApp())
    row {
      completionEnabledCheckbox = checkBox(CodeInsightBundle.message("postfix.completion.option.autopopup"))
        .component
    }
    row(CodeInsightBundle.message("postfix.completion.expand")) {
      shortcutComboBox = comboBox(listOf(TemplateSettings.TAB_CHAR, TemplateSettings.SPACE_CHAR, TemplateSettings.ENTER_CHAR),
                                  textListCellRenderer("") {
                                    when (it) {
                                      TemplateSettings.SPACE_CHAR -> CodeInsightBundle.message("template.shortcut.space")
                                      TemplateSettings.ENTER_CHAR -> CodeInsightBundle.message("template.shortcut.enter")
                                      else -> CodeInsightBundle.message("template.shortcut.tab")
                                    }
                                  })
        .component
    }
    row {
      cell(JBSplitter(false).apply {
        dividerWidth = 20
        firstComponent = createCheckboxTreeDecorator()
        secondComponent = innerPostfixDescriptionPanel.component
      })
        .align(Align.FILL)
    }
      .resizableRow()
  }.withMinimumWidth(500)

  init {
    postfixTemplatesEnabled.addChangeListener { updateComponents() }

    selectionChanges = DebouncedUpdates.forComponent<Unit>(panel, "PostfixTemplatesConfigurableUi", 100.milliseconds)
      .restartTimerOnAdd(true)
      .withContext(Dispatchers.EDT)
      .runLatest { resetDescriptionPanel() }
      .cancelOnDispose(this)
  }

  fun getSelectedShortcut(): Int {
    return (shortcutComboBox.selectedItem as? Char ?: TemplateSettings.TAB_CHAR).code
  }

  override fun dispose() {
    Disposer.dispose(checkboxTree)
    Disposer.dispose(innerPostfixDescriptionPanel)
  }

  fun updateComponents() {
    val pluginEnabled = postfixTemplatesEnabled.isSelected
    completionEnabledCheckbox.isVisible = !LiveTemplateCompletionContributor.shouldShowAllTemplates()
    completionEnabledCheckbox.setEnabled(pluginEnabled)
    postfixTemplatesGroupCompletion.setEnabled(pluginEnabled)
    shortcutComboBox.setEnabled(pluginEnabled)
    checkboxTree.setEnabled(pluginEnabled)
  }

  fun resetDescriptionPanel() {
    innerPostfixDescriptionPanel.reset(PostfixTemplateMetaData.createMetaData(checkboxTree.getSelectedTemplate()))
  }

  private fun createCheckboxTreeDecorator(): JComponent {
    val canAddTemplate = PostfixTemplatesConfigurable.getProviders().any { StringUtil.isNotEmpty(it.presentableName) }

    return ToolbarDecorator.createDecorator(checkboxTree)
      .setAddActionUpdater(AnActionButtonUpdater { canAddTemplate })
      .setAddAction(AnActionButtonRunnable { button -> checkboxTree.addTemplate(button) })
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
      .setEditActionUpdater(AnActionButtonUpdater { checkboxTree.canEditSelectedTemplate() })
      .setEditAction(AnActionButtonRunnable { checkboxTree.editSelectedTemplate() })
      .setRemoveActionUpdater(AnActionButtonUpdater { checkboxTree.canRemoveSelectedTemplates() })
      .setRemoveAction(AnActionButtonRunnable { checkboxTree.removeSelectedTemplates() })
      .addExtraAction(duplicateAction())
      .createPanel()
  }

  private fun duplicateAction(): AnAction {
    val result =
      object : AnAction(CodeInsightBundle.messagePointer("action.AnActionButton.text.duplicate"), PlatformIcons.COPY_ICON) {
        override fun actionPerformed(e: AnActionEvent) {
          checkboxTree.duplicateSelectedTemplate()
        }

        override fun update(e: AnActionEvent) {
          e.presentation.setEnabled(checkboxTree.canDuplicateSelectedTemplate())
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
          return ActionUpdateThread.EDT
        }
      }

    result.registerCustomShortcutSet(CommonShortcuts.getDuplicate(), checkboxTree, checkboxTree)
    return result
  }
}

internal fun createAsyncSettingsInitPlaceholder(
  onLoaded: (PostfixTemplatesSettings) -> JComponent,
  parentDisposable: Disposable,
): JComponent {
  lateinit var settings: PostfixTemplatesSettings
  return AsyncInitPlaceholder(
    {
      withContext(Dispatchers.IO) {
        settings = PostfixTemplatesSettings.getInstance()
      }
    },
    {
      onLoaded(settings)
    },
    parentDisposable,
    "createAsyncSettingsInitPlaceholder"
  )
}
