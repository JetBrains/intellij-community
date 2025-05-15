// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.impl.TextSearchRightActionAction.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.SETabSwitcherListener.Companion.SE_TAB_TOPIC
import com.intellij.ide.actions.searcheverywhere.SETabSwitcherListener.SETabSwitchedEvent
import com.intellij.ide.actions.searcheverywhere.footer.createTextExtendedInfo
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector.wrapEventWithActionStartData
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeOption
import com.intellij.ide.util.scopeChooser.ScopeService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.reference.SoftReference
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.ListCellRenderer

@ApiStatus.Internal
open class TextSearchContributor(val event: AnActionEvent) : WeightedSearchEverywhereContributor<SearchEverywhereItem>,
                                                             SearchFieldActionsContributor,
                                                             SearchEverywhereExtendedInfoProvider,
                                                             PossibleSlowContributor,
                                                             SearchEverywhereEmptyTextProvider,
                                                             SearchEverywherePreviewProvider,
                                                             DumbAware, ScopeSupporting, Disposable {

  private val project = event.getRequiredData(CommonDataKeys.PROJECT)
  private val model = FindManager.getInstance(project).findInProjectModel

  private var everywhereScope = GlobalSearchScope.everythingScope(project)
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

  private fun getProjectScope(descriptors: List<ScopeDescriptor>): GlobalSearchScope? {
    GlobalSearchScope.projectScope(project).takeIf { it != everywhereScope }?.let { return it }

    val secondScope = JBIterable.from(descriptors).filter { !it.scopeEquals(everywhereScope) && !it.scopeEquals(null) }.first()
    return if (secondScope != null) secondScope.scope as GlobalSearchScope? else everywhereScope
  }

  override fun getSearchProviderId(): String = ID
  override fun getGroupName(): @Nls String = FindBundle.message("search.everywhere.group.name")
  override fun getSortWeight(): Int = 1500
  override fun showInFindResults(): Boolean = enabled()
  override fun isShownInSeparateTab(): Boolean = true

  override fun fetchWeightedElements(pattern: String,
                                     indicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<SearchEverywhereItem>>) {
    FindModel.initStringToFind(model, pattern)

    val presentation = FindInProjectUtil.setupProcessPresentation(UsageViewPresentation())

    val scope = GlobalSearchScope.projectScope(project) // TODO use scope from model ?
    val recentItemRef = ThreadLocal<Reference<SearchEverywhereItem>>()
    FindInProjectUtil.findUsages(model, project, indicator, presentation, emptySet()) {
      val usage = UsageInfo2UsageAdapter.CONVERTER.`fun`(it) as UsageInfo2UsageAdapter
      indicator.checkCanceled()

      val recentItem = SoftReference.dereference(recentItemRef.get())
      val newItem = if (recentItem != null && recentItem.usage.merge(usage)) {
        // recompute merged presentation
        recentItem.usage.updateCachedPresentation()
        recentItem.withPresentation(usagePresentation(project, scope, recentItem.usage))
      }
      else {
        usage.updateCachedPresentation()
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
    listOf(ScopeAction { onChanged.run() }, JComboboxAction(project) { onChanged.run() }.also { onDispose = it.saveMask }, PreviewAction())

  override fun createRightActions(registerShortcut: (AnAction) -> Unit, onChanged: Runnable): List<TextSearchRightActionAction> {
    val word = AtomicBooleanProperty(model.isWholeWordsOnly).apply { afterChange { model.isWholeWordsOnly = it } }
    val case = AtomicBooleanProperty(model.isCaseSensitive).apply { afterChange { model.isCaseSensitive = it } }
    val regexp = AtomicBooleanProperty(model.isRegularExpressions).apply { afterChange { model.isRegularExpressions = it } }

    val findModelObserver = FindModel.FindModelObserver {
      if (model.isCaseSensitive != case.get()) case.set(model.isCaseSensitive)
      if (model.isRegularExpressions != regexp.get()) regexp.set(model.isRegularExpressions)
      if (model.isWholeWordsOnly != word.get()) word.set(model.isWholeWordsOnly)
    }

    val connection = ApplicationManager.getApplication().getMessageBus().connect(this)
    connection.subscribe<SETabSwitcherListener>(
      SE_TAB_TOPIC, object : SETabSwitcherListener {
      override fun tabSwitched(event: SETabSwitchedEvent) {
        case.set(false)
        regexp.set(false)
        word.set(false)
      }
    })

    val onDisposeLocal = onDispose
    onDispose = {
      onDisposeLocal.invoke()
      model.removeObserver(findModelObserver)
    }

    model.addObserver(findModelObserver)

    return listOf(CaseSensitiveAction(case, registerShortcut, onChanged),
                  WordAction(word, registerShortcut, onChanged),
                  RegexpAction(regexp, registerShortcut, onChanged))
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
    SE_TEXT_SELECTED_SCOPE.set(project,
                               if (scope.scopeEquals(everywhereScope) || scope.scopeEquals(projectScope)) null else scope.displayName)
    FindSettings.getInstance().customScope = selectedScopeDescriptor.scope?.displayName

    model.customScopeName = selectedScopeDescriptor.scope?.displayName
    model.customScope = selectedScopeDescriptor.scope
    model.isCustomScope = true
  }

  private fun createScopes() = mutableListOf<ScopeDescriptor>().apply {
    addAll(project.service<ScopeService>()
             .createModel(setOf(ScopeOption.LIBRARIES, ScopeOption.EMPTY_SCOPES))
             .getScopesImmediately(AbstractGotoSEContributor.createContext(project, psiContext))
             .scopeDescriptors
    )
  }

  override fun getScope(): ScopeDescriptor = selectedScopeDescriptor
  override fun getSupportedScopes(): MutableList<ScopeDescriptor> = createScopes()

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

    override fun getEverywhereScopeName(): String = everywhereScope.displayName
    override fun getProjectScopeName(): String? = projectScope?.displayName
  }

  override fun dispose() {
    if (this::onDispose.isInitialized) onDispose()
  }

  override fun createExtendedInfo() = createTextExtendedInfo()

  override fun updateEmptyStatus(statusText: StatusText, rebuild: () -> Unit) {
    statusText.appendLine(IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere")).appendText(".").appendLine("")

    if (!(model.isCaseSensitive || model.isWholeWordsOnly || model.isRegularExpressions || model.fileFilter?.isNotBlank() == true)) return

    statusText.appendLine(FindBundle.message("message.nothingFound.used.options")).appendLine("")

    if (model.isCaseSensitive) {
      statusText.appendText(FindBundle.message("find.popup.case.sensitive.label"))
    }
    if (model.isWholeWordsOnly) {
      statusText.appendText(" ").appendText(FindBundle.message("find.whole.words.label"))
    }
    if (model.isRegularExpressions) {
      statusText.appendText(" ").appendText(FindBundle.message("find.regex.label"))
    }
    if (model.fileFilter?.isNotBlank() == true) {
      statusText.appendText(" ").appendText(FindBundle.message("find.popup.filemask.label"))
    }

    val clear = {
      model.isCaseSensitive = false
      model.isWholeWordsOnly = false
      model.isRegularExpressions = false
      model.fileFilter = null
    }

    statusText.appendLine(FindBundle.message("find.popup.clear.all.options"),
                          SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                          ActionListener { _: ActionEvent? -> clear.invoke(); rebuild.invoke() })
  }

  companion object {
    private const val ID = "TextSearchContributor"
    private const val ADVANCED_OPTION_ID = "se.text.search"
    private val SE_TEXT_SELECTED_SCOPE = Key.create<String>("SE_TEXT_SELECTED_SCOPE")

    fun enabled(): Boolean = AdvancedSettings.getBoolean(ADVANCED_OPTION_ID)

    class Factory : SearchEverywhereContributorFactory<SearchEverywhereItem> {
      override fun isAvailable(project: Project): Boolean = enabled()
      override fun createContributor(event: AnActionEvent): TextSearchContributor = TextSearchContributor(event)
    }

    class TextSearchAction : SearchEverywhereBaseAction(), DumbAware {
      override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = enabled()
      }

      override fun actionPerformed(e: AnActionEvent) {
        showInSearchEverywherePopup(ID, wrapEventWithActionStartData(e), true, true)
      }
    }
  }
}
