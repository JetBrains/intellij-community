// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.impl.SETextRightActionAction.*
import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchFieldActionsContributor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.bindStorage
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.PlatformUtils
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.ListCellRenderer

class SETextContributor(val project: Project) : WeightedSearchEverywhereContributor<UsageInfo2UsageAdapter>, SearchFieldActionsContributor, DumbAware {
  val model = FindManager.getInstance(project).findInProjectModel

  override fun getSearchProviderId() = ID
  override fun getGroupName() = FindBundle.message("search.everywhere.group.name")
  override fun getSortWeight() = 1500
  override fun showInFindResults() = false
  override fun isShownInSeparateTab() = true

  override fun fetchWeightedElements(pattern: String, progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<UsageInfo2UsageAdapter>>) {

    FindModel.initStringToFind(model, pattern)

    val presentation = FindInProjectUtil.setupProcessPresentation(project, UsageViewPresentation())
    FindInProjectExecutor.getInstance().findUsages(project, ProgressIndicatorBase(), presentation, model, emptySet()) {
      consumer.process(FoundItemDescriptor<UsageInfo2UsageAdapter>(it as UsageInfo2UsageAdapter, 1500))
      true
    }
  }

  override fun getElementsRenderer(): ListCellRenderer<in UsageInfo2UsageAdapter> = SETextRenderer(GlobalSearchScope.allScope(project))

  override fun processSelectedItem(selected: UsageInfo2UsageAdapter, modifiers: Int, searchText: String): Boolean {
    if (!selected.canNavigate()) return false

    selected.navigate(true)
    return true
  }

  override fun getActions(onChanged: Runnable): List<AnAction> {
    val fileMaskComboboxProperty = AtomicProperty(FindSettings.getInstance().recentFileMasks.last())
      .bindStorage("SE.Text.FileMask.last")
    var fileMaskComboState by fileMaskComboboxProperty

    val fileMaskCheckboxProperty = AtomicBooleanProperty(false).bindBooleanStorage("SE.Text.FileMask.enabled")
    var fileMaskState by fileMaskCheckboxProperty

    val fileMaskCheckbox = object : CheckboxAction(FindBundle.message("find.popup.filemask")) {
      override fun isSelected(e: AnActionEvent) = fileMaskState
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        fileMaskState = state
      }
    }

    class FileMaskComboboxAction : ComboBoxAction() {
      init {
        templatePresentation.setText(Supplier { fileMaskComboState })
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = fileMaskState
      }

      override fun createCustomComponent(presentation: Presentation, place: String): ComboBoxButton =
        createComboBoxButton(presentation).apply {
          putClientProperty("JButton.backgroundColor", JBUI.CurrentTheme.BigPopup.headerBackground())
          text = fileMaskComboState
          fileMaskComboboxProperty.afterChange {
            text = fileMaskComboState
          }
        }

      inner class FileMaskAction(val title: @NlsSafe String) : AnAction(title) {
        override fun actionPerformed(e: AnActionEvent) {
          fileMaskComboState = title
        }
      }

      override fun createPopupActionGroup(button: JComponent?) =
        DefaultActionGroup(FindSettings.getInstance().recentFileMasks.reversed().map { FileMaskAction(it) })
    }

    fun update() {
      model.fileFilter = if (fileMaskState) fileMaskComboState else null
    }

    fileMaskComboboxProperty.afterChange { update(); onChanged.run() }
    fileMaskCheckboxProperty.afterChange { update(); onChanged.run() }
    update()

    return listOf(fileMaskCheckbox, FileMaskComboboxAction())
  }

  override fun createRightActions(onChanged: Runnable): List<SETextRightActionAction> {
    val word = AtomicBooleanProperty(model.isWholeWordsOnly).apply { afterChange { model.isWholeWordsOnly = it } }
    val case = AtomicBooleanProperty(model.isCaseSensitive).apply { afterChange { model.isCaseSensitive = it } }
    val regexp = AtomicBooleanProperty(model.isRegularExpressions).apply { afterChange { model.isRegularExpressions = it } }

    return listOf(CaseSensitiveAction(case, onChanged), WordAction(word, onChanged), RegexpAction(regexp, onChanged))
  }

  override fun getDataForItem(element: UsageInfo2UsageAdapter, dataId: String): UsageInfo2UsageAdapter? = null

  companion object {
    private const val ID = "Text"
    private const val ADVANCED_OPTION_ID = "se.enable.text.search"

    private fun enabled() = AdvancedSettings.getBoolean(ADVANCED_OPTION_ID)

    class Factory : SearchEverywhereContributorFactory<UsageInfo2UsageAdapter> {
      override fun isAvailable() = enabled()
      override fun createContributor(event: AnActionEvent) = SETextContributor(event.getRequiredData(CommonDataKeys.PROJECT))
    }

    class SETextAction : SearchEverywhereBaseAction(), DumbAware {
      override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = enabled()
      }

      override fun actionPerformed(e: AnActionEvent) {
        showInSearchEverywherePopup(ID, e, true, true)
      }
    }

    class SETextActivity : StartupActivity.DumbAware {
      override fun runActivity(project: Project) {
        RunOnceUtil.runOnceForApp(ADVANCED_OPTION_ID) {
          AdvancedSettings.setBoolean(ADVANCED_OPTION_ID, PlatformUtils.isRider())
        }
      }
    }
  }
}