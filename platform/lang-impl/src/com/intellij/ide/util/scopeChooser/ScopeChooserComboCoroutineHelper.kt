// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
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

  fun scheduleRebuildModelAndSelectScope(selection: Any?): Promise<*> = scope.launch(Dispatchers.EDT) {
    val model = DefaultComboBoxModel<ScopeDescriptor>()

    val dataContext = DataManager.getInstance()
      .dataContextFromFocusAsync
      .await()
      .toAsync()
    val descriptors = chooserCombo.processScopes(dataContext)

    chooserCombo.updateModel(model, descriptors)
    chooserCombo.comboBox.model = model

    chooserCombo.selectItem(selection)
    chooserCombo.preselectedScope = null
  }.asPromise()

  @RequiresEdt
  fun scheduleProcessScopes(dataContext: DataContext): Promise<*> {
    val asyncDataContext = dataContext.toAsync()

    return scope.launch(Dispatchers.EDT) {
      chooserCombo.processScopes(asyncDataContext)
    }.asPromise()
  }
}

@RequiresEdt
private suspend fun ScopeChooserCombo.processScopes(dataContext: DataContext): List<ScopeDescriptor> {
  val predefinedScopes = getPredefinedScopesAsync(dataContext).await()

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

private fun DataContext.toAsync() = Utils.wrapToAsyncDataContext(this)