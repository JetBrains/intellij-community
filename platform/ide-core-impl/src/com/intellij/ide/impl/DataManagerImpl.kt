// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.intellij.ide.impl

import com.intellij.ide.DataManager
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.FloatingDecoratorMarker
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.SmartList
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

private val LOG = Logger.getInstance(DataManagerImpl::class.java)

private class ThreadState {
  var depth: Int = 0
  var ids: MutableSet<String>? = null
}

private val ourGetDataLevel: ThreadLocal<ThreadState> = ThreadLocal.withInitial { ThreadState() }

open class DataManagerImpl : DataManager() {

  private val myRulesCache = ConcurrentFactoryMap.createMap<String, GetDataRule> {
    getDataRule(it)
  }

  init {
    val app = ApplicationManager.getApplication()
    app.extensionArea
      .getExtensionPointIfRegistered<KeyedLazyInstance<GetDataRule>>(GetDataRule.EP_NAME.name)
      ?.addChangeListener(Runnable { myRulesCache.clear() }, app)
  }

  @ApiStatus.Internal
  fun getDataFromRules(dataId: String, provider: DataProvider): Any? {
    val rule = myRulesCache[dataId] ?: return null
    return getDataFromRuleInner(rule, dataId, provider)
  }

  private fun getDataFromProviderAndRules(dataId: String, provider: DataProvider): Any? {
    ProgressManager.checkCanceled()
    val state = ourGetDataLevel.get()
    if (state.ids?.contains(dataId) == true) {
      return null
    }
    state.depth++
    try {
      val data = getDataFromProviderInner(dataId, provider, null)
      return data ?: getDataFromRules(dataId, provider)
    }
    finally {
      state.depth--
    }
  }

  private fun getDataFromRuleInner(rule: GetDataRule, dataId: String, provider: DataProvider): Any? {
    val state = ourGetDataLevel.get()
    val ids = state.ids.let { ids ->
      if (ids == null || state.depth == 0) HashSet<String>().also { state.ids = it }
      else ids
    }
    state.depth++
    try {
      ids.add(dataId)
      val data = rule.getData { id ->
        val o = getDataFromProviderAndRules(id, provider)
        if (o === CustomizedDataContext.EXPLICIT_NULL) null else o
      }
      return when {
        data == null -> null
        data === CustomizedDataContext.EXPLICIT_NULL -> data
        else -> DataValidators.validOrNull(data, dataId, rule)
      }
    }
    finally {
      state.depth--
      ids.remove(dataId)
    }
  }

  override fun getCustomizedData(dataId: String, dataContext: DataContext, provider: DataProvider): Any? {
    val data = getDataFromProviderAndRules(dataId) { id ->
      provider.getData(id) ?: dataContext.getData(id)
    }
    return if (data === CustomizedDataContext.EXPLICIT_NULL) null else data
  }

  override fun customizeDataContext(context: DataContext, provider: Any): DataContext {
    val p = when (provider) {
      is DataProvider -> provider
      is UiDataProvider -> EdtNoGetDataProvider { sink -> sink.uiDataSnapshot(provider) }
      is DataSnapshotProvider -> EdtNoGetDataProvider { sink -> sink.dataSnapshot(provider) }
      else -> null
    }
    if (p == null) throw AssertionError("Unexpected provider: " + provider.javaClass.getName())
    return IdeUiService.getInstance().createCustomizedDataContext(context, p)
  }

  private fun getDataRule(dataId: String): GetDataRule? {
    val uninjectedId = InjectedDataKeys.uninjectedId(dataId)
    val noSlowRule = PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(dataId)
    val rules1 = rulesForKey(dataId)
    val rules2 = if (uninjectedId == null) null else rulesForKey(uninjectedId)
    if (rules1 == null && rules2 == null && noSlowRule) return null
    return GetDataRule { dataProvider ->
      val bgtProvider = if (noSlowRule) null else PlatformCoreDataKeys.BGT_DATA_PROVIDER.getData(dataProvider)
      val injectedProvider = if (uninjectedId == null) null
      else DataProvider { id ->
        val injectedId = InjectedDataKeys.injectedId(id)
        if (injectedId != null) dataProvider.getData(injectedId) else null
      }
      getDataFromProviderInner(dataId, bgtProvider, dataProvider)
      ?: (if (uninjectedId == null) null else getDataFromProviderInner(uninjectedId, bgtProvider, injectedProvider))
      ?: (if (rules1 == null) null else getRulesData(dataId, rules1, dataProvider))
      ?: (if (rules2 == null) null else getRulesData(uninjectedId!!, rules2, injectedProvider!!))
    }
  }

  private fun rulesForKey(dataId: String): List<GetDataRule>? {
    var result: MutableList<GetDataRule>? = null
    for (bean in GetDataRule.EP_NAME.extensionsIfPointIsRegistered) {
      if (dataId != bean.getKey()) continue
      val rule = KeyedExtensionCollector.instantiate(bean)
      if (rule == null) continue
      if (result == null) result = SmartList<GetDataRule>()
      result.add(rule)
    }
    return result
  }

  private fun getRulesData(dataId: String, rules: List<GetDataRule>, provider: DataProvider): Any? {
    for (rule in rules) {
      try {
        val data = rule.getData(provider)
        if (data != null) {
          return if (data === CustomizedDataContext.EXPLICIT_NULL) data else DataValidators.validOrNull(data, dataId, rule)
        }
      }
      catch (_: IndexNotReadyException) {
      }
    }
    return null
  }

  private fun getDataFromProviderInner(dataId: String, provider: DataProvider?, outerProvider: DataProvider?): Any? {
    when (provider) {
      null -> return null
      is KeyedDataProvider -> {
        return getDataFromProviderInner(dataId, provider.map[dataId], outerProvider)
               ?: getDataFromProviderInner(dataId, provider.generic, outerProvider)
      }
      is CompositeDataProvider -> {
        for (p in provider.dataProviders) {
          val data = getDataFromProviderInner(dataId, p, outerProvider)
          if (data != null) return data
        }
      }
      else -> {
        try {
          val data = if (provider is ParametrizedDataProvider) outerProvider?.let { provider.getData(dataId, it) }
          else provider.getData(dataId)
          if (data != null) {
            return if (data === CustomizedDataContext.EXPLICIT_NULL) data else DataValidators.validOrNull(data, dataId, provider)
          }
        }
        catch (_: IndexNotReadyException) {
        }
      }
    }
    return null
  }

  override fun getDataContext(component: Component?): DataContext {
    ThreadingAssertions.assertEventDispatchThread()
    if (ourGetDataLevel.get().depth > 0) {
      LOG.error("DataContext shall not be created and queried inside another getData() call.")
    }
    if (component is DependentTransientComponent) {
      LOG.assertTrue(getDataProviderEx(component) == null, "DependentTransientComponent must not yield DataProvider")
    }
    val adjusted = (component as? DependentTransientComponent)?.getPermanentComponent() ?: component
    return IdeUiService.getInstance().createUiDataContext(adjusted)
  }

  override fun getDataContext(component: Component, x: Int, y: Int): DataContext {
    require(x >= 0 && x < component.getWidth() && y >= 0 && y < component.getHeight()) {
      "wrong point: x=$x; y=$y"
    }

    // Point inside JTabbedPane has special meaning. If the point is inside tab bounds, then
    // we construct DataContext by the component which corresponds to the (x, y) tab.
    if (component is JTabbedPane) {
      val index = component.getUI().tabForCoordinate(component, x, y)
      return getDataContext(if (index != -1) component.getComponentAt(index) else component)
    }
    else {
      return getDataContext(component)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun getDataContext(): DataContext {
    var component: Component? = null
    if (`is`("actionSystem.getContextByRecentMouseEvent", false)) {
      component = IdeUiService.getInstance().componentFromRecentMouseEvent
    }
    return getDataContext(component ?: getFocusedComponent())
  }

  override fun getDataContextFromFocusAsync(): Promise<DataContext> {
    val result = AsyncPromise<DataContext>()
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
      { result.setResult(getDataContext()) }, ModalityState.defaultModalityState())
    return result
  }

  private fun getFocusedComponent(): Component? {
    val windowManager = WindowManager.getInstance()

    var activeWindow =
      windowManager.getMostRecentFocusedWindow()
      ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
      ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      ?: return null

    // In case we have an active floating toolwindow and some component in another window focused,
    // we want this other component to receive key events.
    // Walking up the window ownership hierarchy from the floating toolwindow would have led us to the main IdeFrame
    // whereas we want to be able to type in other frames as well.
    if (activeWindow is FloatingDecoratorMarker) {
      val ideFocusManager = IdeFocusManager.findInstanceByComponent(activeWindow)
      val lastFocusedFrame = ideFocusManager.getLastFocusedFrame()
      val frameComponent = lastFocusedFrame?.getComponent()
      val lastFocusedWindow = if (frameComponent != null) SwingUtilities.getWindowAncestor(frameComponent)
      else null
      val toolWindowIsNotFocused = windowManager.getFocusedComponent(activeWindow) == null
      if (toolWindowIsNotFocused && lastFocusedWindow != null) {
        activeWindow = lastFocusedWindow
      }
    }

    // try to find the first parent window that has focus
    var window: Window? = activeWindow
    var focusedComponent: Component? = null
    while (window != null) {
      focusedComponent = windowManager.getFocusedComponent(window)
      if (focusedComponent != null) {
        break
      }
      window = window.owner
    }
    if (focusedComponent == null) {
      focusedComponent = activeWindow
    }
    return focusedComponent
  }

  override fun <T> saveInDataContext(dataContext: DataContext?, dataKey: Key<T>, data: T?) {
    (dataContext as? UserDataHolder)?.putUserData(dataKey, data)
  }

  override fun <T> loadFromDataContext(dataContext: DataContext, dataKey: Key<T>): T? {
    return (dataContext as? UserDataHolder)?.getUserData(dataKey)
  }

  @ApiStatus.Internal
  interface ParametrizedDataProvider {
    fun getData(dataId: String, dataProvider: DataProvider): Any?
  }

  @ApiStatus.Internal
  class KeyedDataProvider(val map: Map<String, DataProvider>, val generic: DataProvider?): DataProvider {
    override fun getData(dataId: String): Nothing = throw UnsupportedOperationException()
  }

  companion object {

    @JvmStatic
    @Deprecated("Most components now implement {@link UiDataProvider} ")
    fun getDataProviderEx(component: Any?): DataProvider? = when (component) {
      is DataProvider -> component
      is JComponent -> getDataProvider(component)
      else -> null
    }

    init {
      for (instance in GetDataRule.EP_NAME.extensionsIfPointIsRegistered) {
        // initialize data-key instances for rules
        val dataKey = DataKey.create<Any>(instance.getKey())
        if (!(instance as GetDataRuleBean).injectedContext) continue
        // add "injected" data-key for rules like "usageTarget"
        InjectedDataKeys.injectedKey(dataKey)
      }
    }

    @JvmStatic
    fun validateEditor(editor: Editor?, contextComponent: Component?): Editor? {
      if (contextComponent is JComponent) {
        if (contextComponent.getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null) return null
      }
      return editor
    }
  }
}
