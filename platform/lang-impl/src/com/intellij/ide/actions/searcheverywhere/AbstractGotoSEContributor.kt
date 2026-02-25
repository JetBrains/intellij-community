// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.EditSourceUtil
import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.gotoByName.ChooseByNameInScopeItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.ChooseByNameWeightedItemProvider
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeOption
import com.intellij.ide.util.scopeChooser.ScopeService
import com.intellij.navigation.AnonymousElementProvider
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.DumbService.Companion.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import com.intellij.util.containers.map2Array
import com.intellij.util.indexing.FindSymbolParameters
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus
import java.util.EnumSet
import java.util.regex.Pattern
import javax.swing.ListCellRenderer

private val LOG = logger<AbstractGotoSEContributor>()
private val SE_SELECTED_SCOPES = Key.create<MutableMap<String, String?>>("SE_SELECTED_SCOPES")

private val ourPatternToDetectLinesAndColumns: Pattern = Pattern.compile(
  "(.+?)" +  // name, non-greedy matching
  "(?::|@|,| |#|#L|\\?l=| on line | at line |:line |:?\\(|:?\\[)" +  // separator
  "(\\d+)?(?:\\W(\\d+)?)?" +  // line + column
  "[)\\]]?" // possible closing paren/brace
)

internal val patternToDetectAnonymousClasses: Pattern = Pattern.compile("([.\\w]+)((\\$\\d+)*(\\$)?)")

abstract class AbstractGotoSEContributor @ApiStatus.Internal protected constructor(
  event: AnActionEvent,
  @ApiStatus.Internal val contributorModules: List<SearchEverywhereContributorModule>?
) : WeightedSearchEverywhereContributor<Any>, ScopeSupporting, SearchEverywhereExtendedInfoProvider {
  @JvmField
  protected val myProject: Project = event.getRequiredData(CommonDataKeys.PROJECT)
  @JvmField
  protected var myScopeDescriptor: ScopeDescriptor

  private val everywhereScope: SearchScope
  private val projectScope: SearchScope
  @JvmField
  protected var isScopeDefaultAndAutoSet: Boolean

  @JvmField
  protected val myPsiContext: SmartPsiElementPointer<PsiElement?>?

  @ApiStatus.Internal
  protected open val navigationHandler: SearchEverywhereNavigationHandler = SearchEverywhereNavigationHandler(project)

  protected constructor(event: AnActionEvent) : this(event, null)

  init {
    contributorModules?.let { modules ->
      modules.forEach { module ->
        Disposer.register(this, module)
      }
    }

    val context = GotoActionBase.getPsiContext(event)
    myPsiContext = if (context == null) null else SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(context)

    @Suppress("LeakingThis")
    val scopeDescriptors = createScopes()
    @Suppress("LeakingThis")
    everywhereScope = findEverywhereScope(scopeDescriptors)
    @Suppress("LeakingThis")
    projectScope = findProjectScope(scopeDescriptors, everywhereScope)
    myScopeDescriptor = getInitialSelectedScope(scopeDescriptors)
    isScopeDefaultAndAutoSet = getSelectedScopes(myProject).get(javaClass.getSimpleName()).isNullOrEmpty()

    @Suppress("LeakingThis")
    myProject.messageBus.connect(this).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        myScopeDescriptor = getInitialSelectedScope(createScopes())
      }
    })
  }

  protected val project: Project
    get() = myProject

  companion object {
    @JvmStatic
    fun createContext(project: Project?, psiContext: SmartPsiElementPointer<PsiElement?>?): DataContext {
      val parentContext = if (project == null) null else SimpleDataContext.getProjectContext(project)
      val context = psiContext?.element
      val file = context?.containingFile

      return SimpleDataContext.builder()
        .setParent(parentContext)
        .add(CommonDataKeys.PSI_ELEMENT, context)
        .add(CommonDataKeys.PSI_FILE, file)
        .build()
    }

    @JvmStatic
    protected fun applyPatternFilter(str: String, regex: Pattern): String {
      val matcher = regex.matcher(str)
      return if (matcher.matches()) matcher.group(1) else str
    }

    fun getElement(element: PsiElement, path: String): PsiElement {
      val classes = path.split('$').dropLastWhile { it.isEmpty() }
      val indexes = IntArrayList()
      for (aClass in classes) {
        if (aClass.isEmpty()) {
          continue
        }

        try {
          indexes.add(aClass.toInt() - 1)
        }
        catch (e: Exception) {
          return element
        }
      }

      var current = element
      val iterator = indexes.intIterator()
      while (iterator.hasNext()) {
        val index = iterator.nextInt()
        val anonymousClasses = getAnonymousClasses(current)
        if (index >= 0 && index < anonymousClasses.size) {
          current = anonymousClasses[index]
        }
        else {
          return current
        }
      }
      return current
    }

    @ApiStatus.Internal
    fun createScopes(project: Project, psiContext: SmartPsiElementPointer<PsiElement?>?): List<ScopeDescriptor> {
      @Suppress("DEPRECATION")
      return project.getService(ScopeService::class.java)
        .createModel(EnumSet.of(ScopeOption.LIBRARIES, ScopeOption.EMPTY_SCOPES))
        .getScopesImmediately(createContext(project, psiContext))
        .scopeDescriptors
    }
  }

  @ApiStatus.Internal
  protected open fun findProjectScope(scopeDescriptors: List<ScopeDescriptor>, everywhereScope: SearchScope): SearchScope {
    val projectScope: SearchScope = GlobalSearchScope.projectScope(myProject)
    if (everywhereScope != projectScope) {
      return projectScope
    }

    // get the second scope, i.e., Attached Directories in DataGrip
    return scopeDescriptors.firstOrNull { !it.scopeEquals(everywhereScope) && !it.scopeEquals(null) }?.scope ?: everywhereScope
  }

  @ApiStatus.Internal
  protected open fun findEverywhereScope(scopeDescriptors: List<ScopeDescriptor>): SearchScope {
    return GlobalSearchScope.everythingScope(myProject)
  }

  protected open fun createScopes(): List<ScopeDescriptor> {
    return createScopes(myProject, myPsiContext)
  }

  override fun getSearchProviderId(): String = javaClass.simpleName

  override fun isShownInSeparateTab(): Boolean = true

  @ApiStatus.Internal
  protected var currentSearchEverywhereAction: SearchEverywhereToggleAction? = null
    private set

  protected fun <T> doGetActions(
    filter: PersistentSearchEverywhereContributorFilter<T>?,
    statisticsCollector: ElementsChooser.StatisticsCollector<T>?,
    onChanged: Runnable,
  ): List<AnAction> {
    if (filter == null) {
      return emptyList()
    }

    val toggleAction = object : ScopeChooserAction() {
      val canToggleEverywhere = everywhereScope != projectScope

      override fun onScopeSelected(o: ScopeDescriptor) {
        setSelectedScope(o)
        onChanged.run()
      }

      override fun getSelectedScope(): ScopeDescriptor = myScopeDescriptor

      override fun onProjectScopeToggled() {
        isEverywhere = !myScopeDescriptor.scopeEquals(everywhereScope)
      }

      override fun processScopes(processor: Processor<in ScopeDescriptor>): Boolean {
        return createScopes().all { processor.process(it) }
      }

      override fun isEverywhere(): Boolean = myScopeDescriptor.scopeEquals(everywhereScope)

      override fun setEverywhere(everywhere: Boolean) {
        setSelectedScope(ScopeDescriptor(if (everywhere) everywhereScope else projectScope))
        onChanged.run()
      }

      override fun canToggleEverywhere(): Boolean {
        if (!canToggleEverywhere) return false
        return myScopeDescriptor.scopeEquals(everywhereScope) ||
               myScopeDescriptor.scopeEquals(projectScope)
      }

      override fun setScopeIsDefaultAndAutoSet(scopeDefaultAndAutoSet: Boolean) {
        isScopeDefaultAndAutoSet = scopeDefaultAndAutoSet
      }

      override fun getEverywhereScopeName(): String = everywhereScope.displayName
      override fun getProjectScopeName(): String = projectScope.displayName
    }

    currentSearchEverywhereAction = toggleAction

    val result = ArrayList<AnAction>()
    result.add(toggleAction)
    result.add(PreviewAction())
    result.add(SearchEverywhereFiltersAction(filter, onChanged, statisticsCollector))
    return result
  }

  private fun getInitialSelectedScope(scopeDescriptors: List<ScopeDescriptor>): ScopeDescriptor {
    val selectedScope = getSelectedScopes(myProject).get(javaClass.simpleName)
    if (!selectedScope.isNullOrEmpty()) {
      for (descriptor in scopeDescriptors) {
        if (selectedScope == descriptor.displayName && !descriptor.scopeEquals(null)) {
          return descriptor
        }
      }
    }
    return ScopeDescriptor(projectScope)
  }

  private fun setSelectedScope(o: ScopeDescriptor) {
    myScopeDescriptor = o
    getSelectedScopes(myProject).put(javaClass.simpleName, if (o.scopeEquals(everywhereScope) || o.scopeEquals(projectScope)) null
    else o.displayName)
  }

  private val fetchers =
    (contributorModules?.map2Array<SearchEverywhereContributorModule, (String, ProgressIndicator, Disposable?, Processor<in FoundItemDescriptor<Any>>) -> Unit> {
      { localPattern, localProgressIndicator, _, localConsumer -> it.perProductFetchWeightedElements(localPattern, localProgressIndicator, localConsumer) }
    } ?: emptyArray()) + { localPattern, localProgressIndicator, op, localConsumer -> performByGotoContributorSearch(localPattern, localProgressIndicator, op, localConsumer) }

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    fetchers.forEach { fetcher -> fetcher(pattern, progressIndicator, null, consumer) }
  }

  @ApiStatus.Internal
  override fun fetchWeightedElementsWithOperationDisposable(
    pattern: String,
    progressIndicator: ProgressIndicator,
    operationDisposable: Disposable,
    consumer: Processor<in FoundItemDescriptor<Any>>
  ) {
    fetchers.forEach { fetcher -> fetcher(pattern, progressIndicator, operationDisposable, consumer) }
  }

  private fun performByGotoContributorSearch(
    pattern: String,
    progressIndicator: ProgressIndicator,
    operationDisposable: Disposable?,
    consumer: Processor<in FoundItemDescriptor<Any>>
  ) {
    if (!isEmptyPatternSupported && pattern.isEmpty()) {
      return
    }

    val fetchRunnable = Runnable {
      if (!isDumbAware && isDumb(myProject)) {
        return@Runnable
      }

      val model = createModelWithOperationDisposable(myProject, operationDisposable)
      if (progressIndicator.isCanceled) {
        return@Runnable
      }

      val context = myPsiContext?.element
      val provider = ChooseByNameModelEx.getItemProvider(model, context)
      val scope = myScopeDescriptor.scope as GlobalSearchScope

      val everywhere = scope.isSearchInLibraries
      val viewModel = MyViewModel(myProject, model)

      if (LOG.isTraceEnabled) {
        LOG.trace(buildString {
          append("!! Collecting Goto SE items for ").append(this@AbstractGotoSEContributor::class.simpleName).append(" !!\n")
          append("PSI context is: ").append(context).append("\n")
          append("Provider is: ").append(provider::class.simpleName).append("\n")
          append("Search scope is: ").append(scope.displayName).append("\n")
          append("Is libraries search? ").append(if (everywhere) "YES" else "NO").append("\n")
        })
      }

      when (provider) {
        is ChooseByNameInScopeItemProvider -> {
          val parameters = FindSymbolParameters.wrap(pattern, scope)
          provider.filterElementsWithWeights(viewModel, parameters, progressIndicator
          ) { item: FoundItemDescriptor<*> ->
            processElement(progressIndicator, consumer, model, item.item, item.weight)
          }
        }
        is ChooseByNameWeightedItemProvider -> {
          provider.filterElementsWithWeights(viewModel, pattern, everywhere, progressIndicator
          ) { item: FoundItemDescriptor<*> ->
            processElement(progressIndicator, consumer, model, item.item, item.weight)
          }
        }
        else -> {
          provider.filterElements(viewModel, pattern, everywhere, progressIndicator) { element: Any ->
            processElement(progressIndicator, consumer, model, element, getElementPriority(element, pattern))
          }
        }
      }
    }

    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode && application.isDispatchThread) {
      fetchRunnable.run()
    }
    else {
      // IJPL-176529
      if (ModalityState.defaultModalityState() == ModalityState.nonModal()) {
        @Suppress("UsagesOfObsoleteApi", "DEPRECATION")
        ProgressIndicatorUtils.yieldToPendingWriteActions()
      }
      @Suppress("UsagesOfObsoleteApi", "DEPRECATION")
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(fetchRunnable, progressIndicator)
    }
  }

  protected open fun processElement(
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
    model: FilteringGotoByModel<*>, element: Any?, degree: Int,
  ): Boolean {
    if (progressIndicator.isCanceled) {
      return false
    }

    if (element == null) {
      LOG.error("Null returned from $model in $this")
      return true
    }

    if (contributorModules?.firstNotNullOf { it.anyElementFitsScope(myScopeDescriptor.scope, element) } == false) {
      return true
    }

    return consumer.process(
      FoundItemDescriptor(
        element, contributorModules?.firstNotNullOf { it.adjustFoundElementWeight(element, degree) } ?: degree
      )
    )
  }

  override fun getScope(): ScopeDescriptor = myScopeDescriptor

  override fun setScope(scope: ScopeDescriptor) {
    setSelectedScope(scope)
  }

  protected val isEverywhere: Boolean
    get() = myScopeDescriptor.scopeEquals(everywhereScope)

  override fun getSupportedScopes(): List<ScopeDescriptor> = createScopes()

  protected abstract fun createModel(project: Project): FilteringGotoByModel<*>

  @ApiStatus.Internal
  protected open fun createModelWithOperationDisposable(project: Project, operationDisposable: Disposable?): FilteringGotoByModel<*> {
    val model = createModel(project)
    if (operationDisposable != null && model is Disposable) {
      Disposer.register(operationDisposable, model)
    }
    return model
  }

  override fun filterControlSymbols(pattern: String): String {
    if (StringUtil.containsAnyChar(pattern, ":,;@[( #") ||
        pattern.contains(" line ") ||
        pattern.contains("?l=")
    ) { // quick test if regexp should be used
      return applyPatternFilter(pattern, ourPatternToDetectLinesAndColumns)
    }

    return pattern
  }

  override fun showInFindResults(): Boolean = true

  // This sets off-the-stack coroutining for some (most) inputs. Take care.
  // a "true" return value does NOT mean that the navigation was successful.
  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    contributorModules?.forEach {
      val processedFlag = it.processSelectedItem(selected, modifiers, searchText)
      if (processedFlag != null) {
        return processedFlag
      }
    }

    return processByGotoSelectedItem(selected, modifiers, searchText)
  }

  @Suppress("SameReturnValue")
  private fun processByGotoSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    if (selected !is PsiElement) {
      if (LOG.isTraceEnabled) {
        LOG.trace("Selected item for $searchText is not PsiElement, it is: ${selected}; performing non-suspending navigation")
      }
      WriteIntentReadAction.run {
        EditSourceUtil.navigate((selected as NavigationItem), true, false)
      }
      return true
    }

    navigationHandler.gotoSelectedItem(selected, modifiers, searchText)

    return true
  }

  override fun getDataForItem(element: Any, dataId: String): Any? {
    if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
      when (element) {
        is PsiElement -> return element
        is DataProvider -> return element.getData(dataId)
        is PsiElementNavigationItem -> return element.targetElement
      }
    }
    return null
  }

  override fun getItemDescription(element: Any): String? {
    return if (element is PsiElement && element.isValid) QualifiedNameProviderUtil.getQualifiedName(element) else null
  }

  override fun isMultiSelectionSupported(): Boolean = true

  override fun isDumbAware(): Boolean = isDumbAware(createModel(myProject))

  override fun getElementsRenderer(): ListCellRenderer<in Any?> =
    contributorModules?.firstNotNullOfOrNull { it.getOverridingElementRenderer(this) }
    ?: SearchEverywherePsiRenderer(this)

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getElementPriority(element: Any, searchPattern: String): Int = 50
}

private class MyViewModel(private val myProject: Project, private val myModel: ChooseByNameModel) : ChooseByNameViewModel {
  override fun getProject(): Project = myProject

  override fun getModel(): ChooseByNameModel = myModel

  override fun isSearchInAnyPlace(): Boolean = myModel.useMiddleMatching()

  override fun transformPattern(pattern: String): String = ChooseByNamePopup.getTransformedPattern(pattern, myModel)

  override fun canShowListForEmptyPattern(): Boolean = false

  override fun getMaximumListSizeLimit(): Int = 0
}

private fun getAnonymousClasses(element: PsiElement): Array<PsiElement> {
  for (provider in AnonymousElementProvider.EP_NAME.extensionList) {
    val elements = provider.getAnonymousElements(element)
    if (elements.isNotEmpty()) {
      return elements
    }
  }
  return PsiElement.EMPTY_ARRAY
}

private fun getSelectedScopes(project: Project): MutableMap<String, String?> {
  var map = SE_SELECTED_SCOPES.get(project)
  if (map == null) {
    SE_SELECTED_SCOPES.set(project, HashMap<String, String?>(3).also { map = it })
  }
  return map
}

