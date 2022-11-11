// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.PredefinedSearchScopeProvider
import com.intellij.util.BitUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import javax.swing.DefaultComboBoxModel

@Internal
private class ScopeChooserComboCoroutineHelper(private val chooserCombo: ScopeChooserCombo) : Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(chooserCombo, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  fun schedulePreselectScopeRebuildModelAndSelectScope(selection: Any?): Promise<*> = scope.launch(Dispatchers.EDT) {
    if (selection != null) {
      chooserCombo.preselectScope(selection)
    }

    chooserCombo.rebuildModelAndSelectScope(selection)
  }.asPromise()

  fun scheduleRebuildModelAndSelectScope(selection: Any?) {
    scope.launch(Dispatchers.EDT) {
      chooserCombo.rebuildModelAndSelectScope(selection)
    }
  }

  @RequiresEdt
  fun scheduleProcessScopes(dataContext: DataContext): Promise<*> {
    val asyncDataContext = dataContext.toAsync()

    return scope.launch(Dispatchers.EDT) {
      chooserCombo.processScopes(asyncDataContext)
    }.asPromise()
  }
}

@RequiresEdt
private suspend fun ScopeChooserCombo.rebuildModelAndSelectScope(selection: Any?) {
  val model = DefaultComboBoxModel<ScopeDescriptor>()

  val dataContext = DataManager.getInstance()
    .dataContextFromFocusAsync
    .await()
    .toAsync()
  val descriptors = processScopes(dataContext)

  updateModel(model, descriptors)
  comboBox.model = model

  selectItem(selection)
  preselectedScope = null
}

@RequiresEdt
private suspend fun ScopeChooserCombo.processScopes(dataContext: DataContext): List<ScopeDescriptor> {
  val predefinedScopes = PredefinedSearchScopeProvider.getInstance(project).getPredefinedScopesAsync(
    dataContext,
    suggestSearchInLibs,
    prevSearchFiles,
    isSet(ScopeChooserCombo.OPT_FROM_SELECTION),
    isSet(ScopeChooserCombo.OPT_USAGE_VIEW),
    isSet(ScopeChooserCombo.OPT_EMPTY_SCOPES),
  )

  return readAction {
    val processor = CommonProcessors.CollectProcessor<ScopeDescriptor>()
    doProcessScopes(
      dataContext,
      predefinedScopes,
      processor,
    )
    processor.results
      .filter { accepts(it) }
  }
}

private suspend fun ScopeChooserCombo.preselectScope(selection: Any?) {
  preselectedScope = PredefinedSearchScopeProvider.getInstance(project).getPredefinedScopesAsync(
    null,
    suggestSearchInLibs,
    prevSearchFiles,
    false,
    false,
    false,
  ).find { it.displayName == selection }
}

private val ScopeChooserCombo.suggestSearchInLibs: Boolean get() = isSet(ScopeChooserCombo.OPT_LIBRARIES)

private val ScopeChooserCombo.prevSearchFiles: Boolean get() = isSet(ScopeChooserCombo.OPT_SEARCH_RESULTS)

private fun ScopeChooserCombo.isSet(mask: Int): Boolean = BitUtil.isSet(options, mask)

private fun DataContext.toAsync() = Utils.wrapToAsyncDataContext(this)