// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.WeighedItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.psi.search.PredefinedSearchScopeProvider
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate


internal class CoroutineScopeModel internal constructor(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  options: Set<ScopeOption>,
) : AbstractScopeModel {

  private val semaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_OLDEST)
  private val listeners = CopyOnWriteArrayList<ScopeModelListener>()
  private val options = mutableSetOf<ScopeOption>().apply { addAll(options) }
  private var filter: (ScopeDescriptor) -> Boolean = { true }

  override fun dispose() {
    listeners.clear()
    coroutineScope.cancel()
  }

  override fun setOption(option: ScopeOption, value: Boolean) {
    if (value) {
      options.add(option)
    }
    else {
      options.remove(option)
    }
  }

  override fun addScopeModelListener(listener: ScopeModelListener) {
    listeners += listener
  }

  override fun removeScopeModelListener(listener: ScopeModelListener) {
    listeners -= listener
  }

  override fun setFilter(filter: (ScopeDescriptor) -> Boolean) {
    this.filter = filter
  }

  override fun refreshScopes(dataContext: DataContext?) {
    LOG.debug("Scope model refresh request")
    val givenDataContext = dataContext?.let { Utils.createAsyncDataContext(it) }
    var earlyDataContextPromise: Promise<DataContext>? = null
    if (givenDataContext == null && EDT.isCurrentThreadEdt()) {
      // We need to capture the data context from focus as early as possible,
      // because focus might change very soon (important when invoking a dialog, e.g., Find in Files).
      earlyDataContextPromise = dataContextFromFocusPromise()
    }
    coroutineScope.launch(
      start = CoroutineStart.UNDISPATCHED,
      context = ModalityState.any().asContextElement() + ClientId.coroutineContext(),
    ) {
      semaphore.withPermit {
        yield() // dispatch
        runCatching {
          LOG.debug("Refreshing scopes")
          val effectiveDataContext: DataContext = when {
            givenDataContext != null -> givenDataContext
            earlyDataContextPromise != null -> earlyDataContextPromise.await()
            else -> dataContextFromFocusPromise().await()
          }
          fireScopesUpdated(getScopeDescriptors(effectiveDataContext, filter))
        }.getOrHandleException {
          LOG.error("Error while refreshing scopes", it)
        }
      }
    }
  }

  private fun dataContextFromFocusPromise(): Promise<DataContext> =
    DataManager.getInstance().dataContextFromFocusAsync
      .then { Utils.createAsyncDataContext(it) }

  private fun fireScopesUpdated(scopesSnapshot: ScopesSnapshot) {
    LOG.debug("Scopes updated")
    listeners.forEach { it.scopesUpdated(scopesSnapshot) }
  }

  override suspend fun getScopes(dataContext: DataContext): ScopesSnapshot = getScopeDescriptors(dataContext, filter)

  @Deprecated("Slow and blocking, use getScopes() in a suspending context, or addScopeModelListener() and refreshScopes()")
  override fun getScopesImmediately(dataContext: DataContext): ScopesSnapshot =
    ScopeModel.getScopeDescriptors(project, dataContext, options, filter)

  private suspend fun getScopeDescriptors(
    dataContext: DataContext,
    filter: Predicate<in ScopeDescriptor>,
  ): ScopesSnapshot {
    val predefinedScopes = PredefinedSearchScopeProvider.getInstance().getPredefinedScopesSuspend(
      project, dataContext,
      options.contains(ScopeOption.LIBRARIES),
      options.contains(ScopeOption.SEARCH_RESULTS),
      options.contains(ScopeOption.FROM_SELECTION),
      options.contains(ScopeOption.USAGE_VIEW),
      options.contains(ScopeOption.EMPTY_SCOPES)
    )
    return doProcessScopes(dataContext, predefinedScopes, filter)
  }

  private suspend fun doProcessScopes(
    dataContext: DataContext,
    predefinedScopes: List<SearchScope>,
    filter: Predicate<in ScopeDescriptor>,
  ): ScopesSnapshot {
    val resultScopes = mutableListOf<ScopeDescriptor>()
    val resultSeparators: HashMap<String, ListSeparator> = HashMap()

    for (searchScope in predefinedScopes) {
      val scopeDescriptor = ScopeDescriptor(searchScope)
      if (filter.test(scopeDescriptor)) {
        resultScopes.add(scopeDescriptor)
      }
    }

    for (provider in ScopeDescriptorProvider.EP_NAME.extensionList) {
      val scopes = readAction {
        runCatching {
          provider.getScopeDescriptors(project, dataContext)
        }.getOrHandleException {
          LOG.error("Couldn't retrieve scope descriptors from $provider", it)
        } ?: emptyArray()
      }
      for (descriptor in scopes) {
        if (filter.test(descriptor)) {
          resultScopes.add(descriptor)
        }
      }
    }

    for (provider in SearchScopeProvider.EP_NAME.extensionList) {
      val separatorName = provider.displayName
      if (separatorName.isNullOrEmpty()) continue
      val scopes = readAction {
        runCatching {
          provider.getSearchScopes(project, dataContext)
        }.getOrHandleException {
          LOG.error("Couldn't retrieve scopes from $provider", it)
        } ?: emptyList()
      }
      if (scopes.isEmpty()) continue
      val scopeSeparator = ScopeSeparator(separatorName)
      if (filter.test(scopeSeparator)) {
        resultScopes.add(scopeSeparator)
      }
      var isFirstScope = false
      for (scope in scopes.sortedWith(comparator)) {
        val scopeDescriptor = ScopeDescriptor(scope)
        if (filter.test(scopeDescriptor)) {
          if (!isFirstScope) {
            isFirstScope = true
            resultSeparators[scope.displayName] = ListSeparator(separatorName)
          }
          resultScopes.add(scopeDescriptor)
        }
      }
    }

    return ScopesSnapshotImpl(resultScopes, resultSeparators)
  }

}

private val comparator = Comparator { o1: SearchScope, o2: SearchScope ->
  val w1 = o1.weight
  val w2 = o2.weight
  if (w1 == w2) return@Comparator StringUtil.naturalCompare(o1.displayName, o2.displayName)
  w1.compareTo(w2)
}

private val SearchScope.weight: Int get() = if (this is WeighedItem) weight else Int.MAX_VALUE

private val LOG = logger<CoroutineScopeModel>()
