// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import org.jetbrains.annotations.ApiStatus
import javax.swing.ComboBoxModel
import javax.swing.ListCellRenderer

/**
 * Defines [ComboBox] with mixed editable and non-editable items.
 * This [ComboBox] automatically changes [isEditable] field, when the [EditableItem] is selected.
 * Also, it can add new [EditableItem] if [SelectEditableItem] is chosen in the [ComboBox] popup.
 *
 * @see EditableItem
 * @see SelectEditableItem
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Suppress("UsePropertyAccessSyntax")
class ComboBoxWithEditableItem<T> : ComboBox<T> {

  constructor() : super()

  constructor(model: ComboBoxModel<T>) : super(model)

  constructor(model: ComboBoxModel<T>, renderer: ListCellRenderer<in T?>) : super(model) {
    setRenderer(renderer)
  }

  override fun setSelectedItem(anObject: Any?) {
    when (anObject) {
      is SelectEditableItem -> {
        setSelectedItem(anObject.createItem())
      }
      is EditableItem -> {
        setEditable(true)
        getEditor().setItem(anObject)
        super.setSelectedItem(anObject)
      }
      else -> {
        setEditable(false)
        super.setSelectedItem(anObject)
      }
    }
  }

  override fun getSelectedItem(): Any? {
    val selectedItem = super.getSelectedItem()
    if (selectedItem is EditableItem) {
      return getEditor().getItem()
    }
    return selectedItem
  }

  /**
   * Defines contract for the editable [ComboBox] item.\
   *
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.getItem
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
   */
  interface EditableItem {

    /**
     * Creates item based on text presentation of the [javax.swing.plaf.basic.BasicComboBoxEditor].
     *
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.getItem
     */
    fun valueOf(value: String): EditableItem

    /**
     * Converts current item to text presentation for the [javax.swing.plaf.basic.BasicComboBoxEditor].
     *
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
     */
    override fun toString(): String
  }

  /**
   * Defines contract for the [ComboBox] item that selects [EditableItem].
   * This action doesn't add [EditableItem] to the [ComboBox] model.
   */
  interface SelectEditableItem {
    fun createItem(): EditableItem
  }
}