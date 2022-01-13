// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerUiUtil")

package com.intellij.openapi.observable.util

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.table.TableView
import com.intellij.util.ui.tree.TreeModelAdapter
import java.awt.ItemSelectable
import java.awt.event.*
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ListModel
import javax.swing.event.*
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel

fun <E> JComboBox<E>.whenItemSelected(listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(listener)
}

fun <E> DropDownLink<E>.whenItemSelected(listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(listener)
}

fun <T> ItemSelectable.whenItemSelected(listener: (T) -> Unit) {
  addItemListener { event ->
    if (event.stateChange == ItemEvent.SELECTED) {
      @Suppress("UNCHECKED_CAST")
      listener(event.item as T)
    }
  }
}

fun ListModel<*>.whenListChanged(listener: (ListDataEvent) -> Unit) {
  addListDataListener(object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent) = listener(e)
    override fun intervalRemoved(e: ListDataEvent) = listener(e)
    override fun contentsChanged(e: ListDataEvent) = listener(e)
  })
}

fun JTree.whenTreeChanged(listener: (TreeModelEvent) -> Unit) {
  model.whenTreeChanged(listener)
}

fun TreeModel.whenTreeChanged(listener: (TreeModelEvent) -> Unit) {
  addTreeModelListener(
    TreeModelAdapter.create { e, _ ->
      listener(e)
    }
  )
}

fun TableView<*>.whenTableChanged(listener: (TableModelEvent) -> Unit) {
  tableViewModel.addTableModelListener {
    listener(it)
  }
}

fun JTextComponent.whenTextChanged(listener: (DocumentEvent) -> Unit) {
  document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      listener(e)
    }
  })
}

fun JComponent.whenFocusGained(listener: (FocusEvent) -> Unit) {
  addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      listener(e)
    }
  })
}

fun JComponent.onceWhenFocusGained(listener: (FocusEvent) -> Unit) {
  addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      removeFocusListener(this)
      listener(e)
    }
  })
}

fun JComponent.whenMousePressed(listener: (MouseEvent) -> Unit) {
  addMouseListener(object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      listener(e)
    }
  })
}