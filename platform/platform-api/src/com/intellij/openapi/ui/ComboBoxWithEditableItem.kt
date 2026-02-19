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
 * @see javax.swing.plaf.basic.BasicComboBoxEditor
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
   * Defines contract for the editable [ComboBox] item.
   *
   * Note: The value of this item can be edited.
   * The editing result will be preserved inside [ComboBox] if this item is added to the [ComboBox] model.
   *
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.getItem
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
   */
  interface EditableItem {

    /**
     * Creates item based on text presentation of the [javax.swing.plaf.basic.BasicComboBoxEditor].
     * By contract of [javax.swing.plaf.basic.BasicComboBoxEditor],
     * this function is used for converting the [ComboBox] editor text to the [ComboBox] item.
     *
     * Note: This function is accessed by reflection inside [javax.swing.plaf.basic.BasicComboBoxEditor.getItem].
     *
     * @see ComboBox.getModel
     * @see ComboBox.getEditor
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.getItem
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
     */
    fun valueOf(value: String): EditableItem

    /**
     * Converts current item to text presentation for the [javax.swing.plaf.basic.BasicComboBoxEditor].
     * By contract of [javax.swing.plaf.basic.BasicComboBoxEditor],
     * this function is used for converting the [ComboBox] item to the [ComboBox] editor text.
     *
     * Note: This function is accessed inside [javax.swing.plaf.basic.BasicComboBoxEditor.setItem].
     *
     * @see ComboBox.getModel
     * @see ComboBox.getEditor
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
     * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
     */
    override fun toString(): String
  }

  /**
   * Defines contract for the [ComboBox] item that creates and selects [EditableItem],
   * when [SelectEditableItem] is chosen in the [ComboBox] popup.
   *
   * Note: This action doesn't add [EditableItem] to the [ComboBox] model.
   *
   * Note: The value of this item cannot be edited.
   * It is a prompt for creating the new [EditableItem] and selecting it.
   *
   * @see ComboBox.getModel
   * @see ComboBox.getPopup
   * @see ComboBox.getEditor
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.getItem
   * @see javax.swing.plaf.basic.BasicComboBoxEditor.setItem
   */
  interface SelectEditableItem {
    fun createItem(): EditableItem
  }
}