// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.find.FindBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.util.*
import javax.swing.plaf.basic.BasicComboBoxEditor
import kotlin.math.min

/**
 * Instances of `ScopeChooserCombo` **must be disposed** when the corresponding dialog or settings page is closed. Otherwise,
 * listeners registered in `init()` cause memory leak.<br></br><br></br>
 * Example: if `ScopeChooserCombo` is used in a
 * `DialogWrapper` subclass, call `Disposer.register(getDisposable(), myScopeChooserCombo)`, where
 * `getDisposable()` is `DialogWrapper`'s method.
 */
@ApiStatus.Internal
class FrontendScopeChooserCombo(project: Project, private val preselectedScopeName: String?, val filterConditionType: ScopesFilterConditionType) : ComboBox<ScopeDescriptor>(400), Disposable {
  private val scopeService = ScopeModelService.getInstance(project)
  private val modelId = UUID.randomUUID().toString()
  private var scopesMap: Map<String, ScopeDescriptor> = emptyMap()
  private val scopeToSeparator: MutableMap<ScopeDescriptor, ListSeparator> = mutableMapOf()

  init {
    setEditor( BasicComboBoxEditor().apply {
      renderer = createScopeDescriptorRenderer ({ descriptor -> scopeToSeparator[descriptor] }, FindBundle.message("find.usages.loading.search.scopes"))
    })

    val cachedScopes = ScopesStateService.getInstance(project).getCachedScopeDescriptors()
    initItems(cachedScopes)
    loadItemsAsync()
  }

  private fun loadItemsAsync() {
    scopeService.loadItemsAsync(modelId, filterConditionType, onFinished = { scopeIdToScopeDescriptor ->
      scopesMap = scopeIdToScopeDescriptor ?: emptyMap()
      val items = scopesMap.values
      withContext(Dispatchers.EDT) {
        initItems(items)
      }
    })
  }

  private fun initItems(items: Collection<ScopeDescriptor>) {
    val previousSelection = selectedItem as? ScopeDescriptor
    removeAllItems()
    items.filterOutSeparators().forEach { addItem(it) }
    tryToSelectItem(items, previousSelection)
  }

  private fun Collection<ScopeDescriptor>.filterOutSeparators(): List<ScopeDescriptor> {
    var lastSeparator: ScopeSeparator? = null
    scopeToSeparator.clear()
    return this.filter { item ->
      if (item is ScopeSeparator) {
        lastSeparator = item
      } else if (lastSeparator != null) {
        scopeToSeparator[item] = ListSeparator(lastSeparator.text)
        lastSeparator = null
      }
      item !is ScopeSeparator
    }
  }

  private fun tryToSelectItem(items: Collection<ScopeDescriptor>, previousSelection: ScopeDescriptor?) {
    items.find { (previousSelection?.displayName ?: preselectedScopeName) == it.displayName }?.let { selectedItem = it }
  }

  override fun setMinimumSize(minimumSize: Dimension?) {
    super.setMinimumSize(minimumSize)
  }

  override fun getPreferredSize(): Dimension {
    if (isPreferredSizeSet) {
      return super.getPreferredSize()
    }
    val preferredSize = super.getPreferredSize()
    return Dimension(min(400, preferredSize.width), preferredSize.height)
  }

  fun getSelectedScopeId(): String? {
    val scopeDescriptor = selectedItem as? ScopeDescriptor
    return scopesMap.entries.firstOrNull { it.value == scopeDescriptor }?.key
  }

  @Nls
  fun getSelectedScopeName(): String? {
    return (selectedItem as? ScopeDescriptor)?.displayName
  }


  override fun dispose() {
    scopeService.disposeModel(modelId)
    // ActionListener[] listeners = myBrowseButton.getActionListeners();
    //    for (ActionListener listener : listeners) {
    //      myBrowseButton.removeActionListener(listener);
    //    }
  }

}