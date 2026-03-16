// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.find.FindBundle
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.popup.ListSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.UUID
import javax.swing.JPanel
import kotlin.math.min

/**
 * Instances of `ScopeChooserCombo` **must be disposed** when the corresponding dialog or settings page is closed. Otherwise,
 * listeners registered in `init()` cause memory leak.<br></br><br></br>
 * Example: if `ScopeChooserCombo` is used in a
 * `DialogWrapper` subclass, call `Disposer.register(getDisposable(), myScopeChooserCombo)`, where
 * `getDisposable()` is `DialogWrapper`'s method.
 */
@ApiStatus.Internal
class FrontendScopeChooser(private val project: Project, private val preselectedScopeName: String?, private val filterConditionType: ScopesFilterConditionType = ScopesFilterConditionType.OTHER) : JPanel(BorderLayout()), Disposable {
  private val scopeService = ScopeModelService.getInstance(project)
  private val modelId = UUID.randomUUID().toString()
  private var scopesMap: Map<String, ScopeDescriptor> = emptyMap()
  private val scopeToSeparator: MutableMap<ScopeDescriptor, ListSeparator> = mutableMapOf()

  private val comboBox = ComboBox<ScopeDescriptor>(300)
  private var initialSelection: ScopeDescriptor? = null
  private var selectedItem: ScopeDescriptor?
    get() = comboBox.selectedItem as? ScopeDescriptor
    set(value) {
      if (selectedItem == value) return
      comboBox.setSelectedItem(value)
    }

  private val editScopesButton = FixedSizeButton(comboBox).apply {
    addActionListener { editScopes() }
    accessibleContext.accessibleName = FindBundle.message("find.usages.edit.scopes.button.accessible.name")
  }

  init {
    comboBox.renderer = createScopeDescriptorRenderer({ descriptor -> scopeToSeparator[descriptor] }, FindBundle.message("find.usages.loading.search.scopes"))
    comboBox.setSwingPopup(false)
    comboBox.whenItemSelected {
      val scopeId = getSelectedScopeId() ?: return@whenItemSelected
      if (it.needsUserInputForScope()) {
        FindAndReplaceExecutor.getInstance().performScopeSelection(scopeId, project)
      }
    }
    comboBox.accessibleContext.accessibleName = FindBundle.message("find.usages.scope.combobox.accessible.name")

    val cachedScopes = ScopesStateService.getInstance(project).getCachedScopeDescriptors()
    initItems(cachedScopes)
    initialSelection = selectedItem
    loadItemsAsync()

    add(comboBox, BorderLayout.CENTER)
    add(editScopesButton, BorderLayout.EAST)
  }

  private fun loadItemsAsync() {
    // loadItemsAsync will be executed on asynchronously on background,
    // it's important to collect data context now,
    // because it's going to change with opening Find in Files dialog
    val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync.then { Utils.createAsyncDataContext(it) }
    scopeService.loadItemsAsync(modelId, filterConditionType, dataContextPromise, onScopesUpdate = { scopeIdToScopeDescriptor, selectedScopeId ->
      scopesMap = scopeIdToScopeDescriptor ?: emptyMap()
      val items = scopesMap.values.toList()
      withContext(Dispatchers.EDT) {
        initItems(items, selectedScopeId)
      }
    })
  }

  fun getComboBox(): ComboBox<ScopeDescriptor> = comboBox

  private fun initItems(items: List<ScopeDescriptor>, selectedScopeId: String? = null) {
    // Avoid using initial selection as a previous selection
    // it blocks setting a correct one after receiving data from backend for the first time
    val previousSelection = selectedScopeId?.let { scopesMap[it] } ?: selectedItem.takeIf { it != initialSelection }
    initialSelection = null
    comboBox.removeAllItems()
    items.filterOutSeparators().forEach { comboBox.addItem(it) }
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
    items.find { (previousSelection?.displayName ?: preselectedScopeName) == it.displayName }?.let {
      if (!it.needsUserInputForScope()) selectedItem = it
    }
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
    val scopeDescriptor = selectedItem
    return scopesMap.entries.firstOrNull { it.value == scopeDescriptor }?.key
  }

  @Nls
  fun getSelectedScopeName(): String? {
    return selectedItem?.displayName
  }


  override fun dispose() {
    scopeService.disposeModel(modelId)
    editScopesButton.actionListeners.forEach { editScopesButton.removeActionListener(it) }
  }

  private fun editScopes() {
    val selection = getSelectedScopeId()
    ScopeModelService.getInstance(project).openEditScopesDialog(selection, modelId) { scopeId ->
      ApplicationManager.getApplication().invokeLater {
        scopeId?.let { selectedItem = scopesMap[it] }
      }
    }
  }
}