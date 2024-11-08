// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.actions.searcheverywhere

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.EditSourceUtil
import com.intellij.ide.util.ElementsChooser
import com.intellij.ide.util.gotoByName.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeOption
import com.intellij.ide.util.scopeChooser.ScopeService
import com.intellij.navigation.AnonymousElementProvider
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.DumbService.Companion.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IntPair
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import it.unimi.dsi.fastutil.ints.IntArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.util.*
import java.util.regex.Matcher
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

abstract class AbstractGotoSEContributor protected constructor(event: AnActionEvent)
  : WeightedSearchEverywhereContributor<Any>, ScopeSupporting, SearchEverywhereExtendedInfoProvider {
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

  init {
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

    @JvmStatic
    protected fun getLineAndColumn(text: String): IntPair {
      var line = getLineAndColumnRegexpGroup(text, 2)
      val column = getLineAndColumnRegexpGroup(text, 3)

      if (line == -1 && column != -1) {
        line = 0
      }

      return IntPair(line, column)
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
    @Suppress("DEPRECATION")
    return myProject.getService(ScopeService::class.java)
      .createModel(EnumSet.of(ScopeOption.LIBRARIES, ScopeOption.EMPTY_SCOPES))
      .getScopesImmediately(createContext(myProject, myPsiContext))
      .scopeDescriptors
  }

  override fun getSearchProviderId(): String = javaClass.simpleName

  override fun isShownInSeparateTab(): Boolean = true

  protected fun <T> doGetActions(
    filter: PersistentSearchEverywhereContributorFilter<T>?,
    statisticsCollector: ElementsChooser.StatisticsCollector<T>?,
    onChanged: Runnable,
  ): List<AnAction> {
    if (filter == null) {
      return emptyList()
    }

    val result = ArrayList<AnAction>()
    result.add(object : ScopeChooserAction() {
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
    })
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

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    if (!isEmptyPatternSupported && pattern.isEmpty()) {
      return
    }

    val fetchRunnable = Runnable {
      if (!isDumbAware && isDumb(myProject)) {
        return@Runnable
      }

      val model = createModel(myProject)
      if (progressIndicator.isCanceled) {
        return@Runnable
      }

      val context = myPsiContext?.element
      val provider = ChooseByNameModelEx.getItemProvider(model, context)
      val scope = myScopeDescriptor.scope as GlobalSearchScope

      val everywhere = scope.isSearchInLibraries
      val viewModel = MyViewModel(myProject, model)
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
      @Suppress("UsagesOfObsoleteApi", "DEPRECATION")
      ProgressIndicatorUtils.yieldToPendingWriteActions()
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

    return consumer.process(FoundItemDescriptor(element, degree))
  }

  override fun getScope(): ScopeDescriptor = myScopeDescriptor

  override fun setScope(scope: ScopeDescriptor) {
    setSelectedScope(scope)
  }

  protected val isEverywhere: Boolean
    get() = myScopeDescriptor.scopeEquals(everywhereScope)

  override fun getSupportedScopes(): List<ScopeDescriptor> = createScopes()

  protected abstract fun createModel(project: Project): FilteringGotoByModel<*>

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

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    if (selected !is PsiElement) {
      EditSourceUtil.navigate((selected as NavigationItem), true, false)
      return true
    }

    project.service<SearchEverywhereContributorCoroutineScopeHolder>().coroutineScope.launch(ClientId.coroutineContext()) {
      val command = readAction {
        if (!selected.isValid) {
          LOG.warn("Cannot navigate to invalid PsiElement")
          return@readAction null
        }

        val psiElement = preparePsi(selected, searchText)
        val file = PsiUtilCore.getVirtualFile(psiElement)
        val extendedNavigatable = if (file == null) {
          null
        }
        else {
          val position = getLineAndColumn(searchText)
          if (position.first >= 0 || position.second >= 0) {
            //todo create navigation request by line&column, not by offset only
            OpenFileDescriptor(project, file, position.first, position.second)
          }
          else {
            null
          }
        }

        suspend {
          @Suppress("DEPRECATION")
          val navigationOptions = NavigationOptions.defaultOptions()
            .openInRightSplit((modifiers and InputEvent.SHIFT_MASK) != 0)
            .preserveCaret(true)
          if (extendedNavigatable == null) {
            if (file == null) {
              val navigatable = psiElement as? Navigatable
              if (navigatable != null) {
                // Navigation items from rd protocol often lack .containingFile or other PSI extensions, and are only expected to be
                // navigated through the Navigatable API.
                // This fallback is for items like that.
                val navRequest = RawNavigationRequest(navigatable, true)
                project.serviceAsync<NavigationService>().navigate(navRequest, navigationOptions)
              } else {
                LOG.warn("Cannot navigate to invalid PsiElement (psiElement=$psiElement, selected=$selected)")
              }
            }
            else {
              createSourceNavigationRequest(element = psiElement, file = file, searchText = searchText)?.let {
                project.serviceAsync<NavigationService>().navigate(it, navigationOptions)
              }
            }
          }
          else {
            project.serviceAsync<NavigationService>().navigate(extendedNavigatable, navigationOptions)
            triggerLineOrColumnFeatureUsed(extendedNavigatable)
          }
        }
      }

      command?.invoke()
    }

    return true
  }

  @ApiStatus.Internal
  protected open suspend fun createSourceNavigationRequest(
    element: PsiElement,
    file: com.intellij.openapi.vfs.VirtualFile,
    searchText: String,
  ): NavigationRequest? {
    if (element is Navigatable) {
      return readAction {
        element.navigationRequest()
      }
    }
    else {
      val navigationRequests = serviceAsync<NavigationRequests>()
      return readAction {
        navigationRequests.sourceNavigationRequest(project = project, file = file, offset = element.textOffset, elementRange = null)
      }
    }
  }

  protected open suspend fun triggerLineOrColumnFeatureUsed(extendedNavigatable: Navigatable) {
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

  override fun getElementsRenderer(): ListCellRenderer<in Any?> = SearchEverywherePsiRenderer(this)

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getElementPriority(element: Any, searchPattern: String): Int = 50

  private fun preparePsi(originalPsiElement: PsiElement, searchText: String): PsiElement {
    var psiElement = originalPsiElement
    pathToAnonymousClass(searchText)?.let {
      psiElement = getElement(psiElement, it)
    }
    return psiElement.navigationElement
  }
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

private fun getLineAndColumnRegexpGroup(text: String, groupNumber: Int): Int {
  val matcher = ourPatternToDetectLinesAndColumns.matcher(text)
  if (matcher.matches()) {
    try {
      if (groupNumber <= matcher.groupCount()) {
        val group = matcher.group(groupNumber)
        if (group != null) return group.toInt() - 1
      }
    }
    catch (ignored: NumberFormatException) {
    }
  }

  return -1
}

@Service(Service.Level.PROJECT)
private class SearchEverywhereContributorCoroutineScopeHolder(coroutineScope: CoroutineScope) {
  @JvmField val coroutineScope: CoroutineScope = coroutineScope.childScope("SearchEverywhereContributorCoroutineScopeHolder")
}

private fun pathToAnonymousClass(searchedText: String): String? {
  return pathToAnonymousClass(patternToDetectAnonymousClasses.matcher(searchedText))
}

internal fun pathToAnonymousClass(matcher: Matcher): String? {
  if (matcher.matches()) {
    var path = matcher.group(2)?.trim() ?: return null
    if (path.endsWith('$') && path.length >= 2) {
      path = path.substring(0, path.length - 2)
    }
    if (!path.isEmpty()) {
      return path
    }
  }

  return null
}
