// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.impl.SETextRightActionAction.*
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.SearchEverywhereClassifier
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor.createContext
import com.intellij.ide.util.RunOnceUtil
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Key
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.CommonProcessors
import com.intellij.util.PlatformUtils
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import javax.swing.ListCellRenderer

class SETextContributor(val event: AnActionEvent) : WeightedSearchEverywhereContributor<UsageInfo2UsageAdapter>,
                                                    SearchFieldActionsContributor, DumbAware, ScopeSupporting {

  private val project = event.getRequiredData(CommonDataKeys.PROJECT)
  private val model = FindManager.getInstance(project).findInProjectModel

  private var everywhereScope = getEverywhereScope()
  private var projectScope: GlobalSearchScope?
  private var selectedScopeDescriptor: ScopeDescriptor
  private var psiContext = getPsiContext()

  init {
    val scopes = createScopes()
    projectScope = getProjectScope(scopes)
    selectedScopeDescriptor = getInitialSelectedScope(scopes)
  }

  private fun getPsiContext() = GotoActionBase.getPsiContext(event)?.let {
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
  }

  private fun getEverywhereScope() =
    SearchEverywhereClassifier.EP_Manager.getEverywhereScope(project) ?: GlobalSearchScope.everythingScope(project)

  private fun getProjectScope(descriptors: List<ScopeDescriptor>): GlobalSearchScope? {
    SearchEverywhereClassifier.EP_Manager.getProjectScope(project)?.let { return it }

    GlobalSearchScope.projectScope(project).takeIf { it != everywhereScope }?.let { return it }

    val secondScope = JBIterable.from(descriptors).filter { !it.scopeEquals(everywhereScope) && !it.scopeEquals(null) }.first()
    return if (secondScope != null) secondScope.scope as GlobalSearchScope? else everywhereScope
  }

  override fun getSearchProviderId() = ID
  override fun getGroupName() = FindBundle.message("search.everywhere.group.name")
  override fun getSortWeight() = 1500
  override fun showInFindResults() = false
  override fun isShownInSeparateTab() = true

  override fun fetchWeightedElements(pattern: String,
                                     indicator: ProgressIndicator,
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

  override fun getActions(onChanged: Runnable): List<AnAction> =
    listOf(ScopeAction { onChanged.run() }, JComboboxAction(project) { onChanged.run() })

  override fun createRightActions(onChanged: Runnable): List<SETextRightActionAction> {
    val word = AtomicBooleanProperty(model.isWholeWordsOnly).apply { afterChange { model.isWholeWordsOnly = it } }
    val case = AtomicBooleanProperty(model.isCaseSensitive).apply { afterChange { model.isCaseSensitive = it } }
    val regexp = AtomicBooleanProperty(model.isRegularExpressions).apply { afterChange { model.isRegularExpressions = it } }

    return listOf(CaseSensitiveAction(case, onChanged), WordAction(word, onChanged), RegexpAction(regexp, onChanged))
  }

  override fun getDataForItem(element: UsageInfo2UsageAdapter, dataId: String): UsageInfo2UsageAdapter? = null

  private fun getInitialSelectedScope(scopeDescriptors: List<ScopeDescriptor>): ScopeDescriptor {
    val scope = SE_TEXT_SELECTED_SCOPE.get(project) ?: return ScopeDescriptor(projectScope)

    return scopeDescriptors.find { scope == it.displayName && !it.scopeEquals(null) } ?: ScopeDescriptor(projectScope)
  }

  private fun setSelectedScope(scope: ScopeDescriptor) {
    selectedScopeDescriptor = scope
    SE_TEXT_SELECTED_SCOPE.set(project, if (scope.scopeEquals(everywhereScope) || scope.scopeEquals(projectScope)) null else scope.displayName)
    FindSettings.getInstance().customScope = selectedScopeDescriptor.scope?.displayName

    model.customScopeName = selectedScopeDescriptor.scope?.displayName
    model.customScope = selectedScopeDescriptor.scope
    model.isCustomScope = true
  }

  private fun createScopes() = mutableListOf<ScopeDescriptor>().also {
    ScopeChooserCombo.processScopes(project, createContext(project, psiContext),
                                    ScopeChooserCombo.OPT_LIBRARIES or ScopeChooserCombo.OPT_EMPTY_SCOPES,
                                    CommonProcessors.CollectProcessor(it))
  }

  override fun getScope() = selectedScopeDescriptor
  override fun getSupportedScopes() = createScopes()

  override fun setScope(scope: ScopeDescriptor) {
    setSelectedScope(scope)
  }

  private inner class ScopeAction(val onChanged: () -> Unit) : ScopeChooserAction() {
    override fun onScopeSelected(descriptor: ScopeDescriptor) {
      setSelectedScope(descriptor)
      onChanged()
    }

    override fun getSelectedScope() = selectedScopeDescriptor
    override fun isEverywhere() = selectedScopeDescriptor.scopeEquals(everywhereScope)
    override fun processScopes(processor: Processor<in ScopeDescriptor>) = ContainerUtil.process(createScopes(), processor)

    override fun onProjectScopeToggled() {
      isEverywhere = !selectedScopeDescriptor.scopeEquals(everywhereScope)
    }

    override fun setEverywhere(everywhere: Boolean) {
      setSelectedScope(ScopeDescriptor(if (everywhere) everywhereScope else projectScope))
      onChanged()
    }

    override fun canToggleEverywhere() = if (everywhereScope == projectScope) false
    else selectedScopeDescriptor.scopeEquals(everywhereScope) || selectedScopeDescriptor.scopeEquals(projectScope)
  }

  companion object {
    private const val ID = "Text"
    private const val ADVANCED_OPTION_ID = "se.enable.text.search"
    private val SE_TEXT_SELECTED_SCOPE = Key.create<String>("SE_TEXT_SELECTED_SCOPE")

    private fun enabled() = AdvancedSettings.getBoolean(ADVANCED_OPTION_ID)

    class Factory : SearchEverywhereContributorFactory<UsageInfo2UsageAdapter> {
      override fun isAvailable() = enabled()
      override fun createContributor(event: AnActionEvent) = SETextContributor(event)
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