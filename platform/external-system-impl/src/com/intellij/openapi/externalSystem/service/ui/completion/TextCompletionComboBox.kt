// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.observable.util.whenListChanged
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addExtension
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.ui.*
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TextCompletionComboBox<T>(
  project: Project?,
  private val converter: TextCompletionComboBoxConverter<T>
) : TextCompletionField<T>(project) {

  val collectionModel = CollectionComboBoxModel<T>()

  val selectedItemProperty = AtomicProperty(converter.getItem(""))
  var selectedItem by selectedItemProperty

  override fun getCompletionVariants(): List<T> {
    return collectionModel.items
  }

  private fun getItem(text: String): T {
    val item = converter.getItem(text)
    val existedItem = collectionModel.items.find { it == item }
    return existedItem ?: item
  }

  fun bindSelectedItem(property: ObservableMutableProperty<T>) {
    selectedItemProperty.bind(property)
  }

  init {
    renderer = converter
    completionType = CompletionType.REPLACE_TEXT
  }

  init {
    bind(selectedItemProperty.transform(converter::getText, ::getItem))
    collectionModel.whenListChanged {
      selectedItem = getItem(text)
    }
  }

  init {
    collectionModel.whenListChanged {
      updatePopup(UpdatePopupType.UPDATE)
    }
    addExtension(AllIcons.General.ArrowDown, AllIcons.General.ArrowDown, null) {
      updatePopup(UpdatePopupType.SHOW_ALL_VARIANCES)
    }
    addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)) {
      updatePopup(UpdatePopupType.SHOW_ALL_VARIANCES)
    }
  }
}