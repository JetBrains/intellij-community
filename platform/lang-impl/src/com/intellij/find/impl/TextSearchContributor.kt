// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.impl.TextSearchRightActionAction.*
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.SearchEverywhereClassifier
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor.createContext
import com.intellij.ide.util.RunOnceUtil
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.reference.SoftReference
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.PlatformUtils
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.ListCellRenderer

internal class TextSearchContributor(
  val event: AnActionEvent
) : WeightedSearchEverywhereContributor<SearchEverywhereItem>,
    SearchFieldActionsContributor,
    PossibleSlowContributor,
    DumbAware, ScopeSupporting, Disposable {

  private val project = event.getRequiredData(CommonDataKeys.PROJECT)
  private val model = FindManager.getInstance(project).findInProjectModel

  private var everywhereScope = getEverywhereScope()
  private var projectScope: GlobalSearchScope?
  private var selectedScopeDescriptor: ScopeDescriptor
  private var psiContext = getPsiContext()

  private lateinit var onDispose: () -> Unit

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
  override fun showInFindResults() = enabled()
  override fun isShownInSeparateTab() = true

  override fun fetchWeightedElements(pattern: String,
                                     indicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<SearchEverywhereItem>>) {
    FindModel.initStringToFind(model, pattern)

    val presentation = FindInProjectUtil.setupProcessPresentation(project, UsageViewPresentation())

    val scope = GlobalSearchScope.projectScope(project) // TODO use scope from model ?
    val recentItemRef = ThreadLocal<Reference<SearchEverywhereItem>>()
    FindInProjectUtil.findUsages(model, project, indicator, presentation, emptySet()) {
      val usage = UsageInfo2UsageAdapter.CONVERTER.`fun`(it) as UsageInfo2UsageAdapter
      indicator.checkCanceled()

      val recentItem = SoftReference.dereference(recentItemRef.get())
      val newItem = if (recentItem != null && recentItem.usage.merge(usage)) {
        // recompute merged presentation
        recentItem.withPresentation(usagePresentation(project, scope, recentItem.usage))
      }
      else {
        SearchEverywhereItem(usage, usagePresentation(project, scope, usage)).also {
         if (!consumer.process(FoundItemDescriptor(it, 0))) return@findUsages false
        }
      }
      recentItemRef.set(WeakReference(newItem))

      true
    }
  }

  override fun getElementsRenderer(): ListCellRenderer<in SearchEverywhereItem> = TextSearchRenderer()

  override fun processSelectedItem(selected: SearchEverywhereItem, modifiers: Int, searchText: String): Boolean {
    // TODO async navigation
    val info = selected.usage
    if (!info.canNavigate()) return false

    info.navigate(true)
    return true
  }

  override fun getActions(onChanged: Runnable): List<AnAction> =
    listOf(ScopeAction { onChanged.run() }, JComboboxAction(project) { onChanged.run() }.also { onDispose = it.saveMask })

  override fun createRightActions(onChanged: Runnable): List<TextSearchRightActionAction> {
    lateinit var regexp: AtomicBooleanProperty
    val word = AtomicBooleanProperty(model.isWholeWordsOnly).apply { afterChange { model.isWholeWordsOnly = it; if (it) regexp.set(false) } }
    val case = AtomicBooleanProperty(model.isCaseSensitive).apply { afterChange { model.isCaseSensitive = it } }
    regexp = AtomicBooleanProperty(model.isRegularExpressions).apply { afterChange { model.isRegularExpressions = it; if (it) word.set(false) } }

    return listOf(CaseSensitiveAction(case, onChanged), WordAction(word, onChanged), RegexpAction(regexp, onChanged))
  }

  override fun getDataForItem(element: SearchEverywhereItem, dataId: String): Any? {
    if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
      return element.usage.element
    }

    return null
  }

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

  private fun createScopes() = mutableListOf<ScopeDescriptor>().apply {
    addAll(ScopeModel.getScopeDescriptors(project, createContext(project, psiContext),
                                          setOf(ScopeModel.Option.LIBRARIES, ScopeModel.Option.EMPTY_SCOPES)))
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

  override fun dispose() {
    if (this::onDispose.isInitialized) onDispose()
  }

  companion object {
    private const val ID = "TextSearchContributor"
    private const val ADVANCED_OPTION_ID = "se.text.search"
    private val SE_TEXT_SELECTED_SCOPE = Key.create<String>("SE_TEXT_SELECTED_SCOPE")

    private fun enabled() = AdvancedSettings.getBoolean(ADVANCED_OPTION_ID)

    class Factory : SearchEverywhereContributorFactory<SearchEverywhereItem> {
      override fun isAvailable() = enabled()
      override fun createContributor(event: AnActionEvent) = TextSearchContributor(event)
    }

    class TextSearchAction : SearchEverywhereBaseAction(), DumbAware {
      override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = enabled()
      }

      override fun actionPerformed(e: AnActionEvent) {
        showInSearchEverywherePopup(ID, e, true, true)
      }
    }

    internal class TextSearchActivity : ProjectActivity {
      override suspend fun execute(project: Project) {
        RunOnceUtil.runOnceForApp(ADVANCED_OPTION_ID) {
          AdvancedSettings.setBoolean(ADVANCED_OPTION_ID, PlatformUtils.isRider())
        }
      }
    }
  }
}