// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.util.*
import javax.swing.Icon
import javax.swing.JTextField
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
class FrontendScopeChooserCombo(project: Project) : ComboBox<ScopeDescriptor>(), Disposable {
  private val scopeService = ScopeModelService.getInstance(project)
  private val modelId = UUID.randomUUID().toString()
  private var loadingTextComponent: ExtendableTextField? = null
  private var scopesMap: Map<String, ScopeDescriptor> = emptyMap()
  private val scopeToSeparator: MutableMap<ScopeDescriptor, ListSeparator> = mutableMapOf()

  private val browseExtension: ExtendableTextComponent.Extension = ExtendableTextComponent.Extension.create(AllIcons.General.ArrowDown, "", //TODO()
                                                                                                                                       { TODO() })
  private val loadingExtension: ExtendableTextComponent.Extension = ExtendableTextComponent.Extension.create(AnimatedIcon.Default(), "", { TODO() })


  init {
    loadItemsAsync()
    setEditor(object : BasicComboBoxEditor() {
      override fun createEditorComponent(): JTextField {
        val ecbEditor = ExtendableTextField()
        ecbEditor.addExtension(browseExtension)
        ecbEditor.setBorder(null)
        loadingTextComponent = ecbEditor
        return ecbEditor
      }
    }.apply {
      renderer = createScopeDescriptorRenderer { descriptor -> scopeToSeparator[descriptor] }
    })
  }

  private fun setLoading(loading: Boolean) {
    isEnabled = !loading

    loadingTextComponent?.let { editor ->

      editor.removeExtension(loadingExtension)
      editor.removeExtension(browseExtension)
      if (loading) { // Add loading indicator extension
        editor.addExtension(loadingExtension)
      }
      editor.addExtension(browseExtension)
      editor.repaint()
    }
  }

  private fun loadItemsAsync() {
    setLoading(true)

    scopeService.loadItemsAsync(modelId, onFinished = { scopeIdToScopeDescriptor ->
      scopesMap = scopeIdToScopeDescriptor ?: emptyMap()
      val items = scopesMap.values
      withContext(Dispatchers.EDT) {
        removeAllItems()
        items.filterOutSeparators().forEach { addItem(it) }
        setLoading(false)
      }
    })
  }

  private fun Collection<ScopeDescriptor>.filterOutSeparators(): List<ScopeDescriptor> {
    var lastItem: ScopeDescriptor? = null

    return this.filter { item ->
      if (item is ScopeSeparator) {
        if (lastItem != null) {
          scopeToSeparator[lastItem] = ListSeparator(item.text)
        }
      }
      lastItem = item
      item !is ScopeSeparator
    }
  }

  override fun getPreferredSize(): Dimension? {
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
    scopeService.disposeModel(modelId) // ActionListener[] listeners = myBrowseButton.getActionListeners();
    //    for (ActionListener listener : listeners) {
    //      myBrowseButton.removeActionListener(listener);
    //    }
  }

}


@ApiStatus.Internal
data class SearchScopeUiInfo(val id: String, val name: String, val icon: Icon?, val isSeparator: Boolean)