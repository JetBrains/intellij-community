// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerUiUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.table.TableView
import com.intellij.util.ui.TableViewModel
import com.intellij.util.ui.tree.TreeModelAdapter
import java.awt.ItemSelectable
import java.awt.event.*
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.ListModel
import javax.swing.event.*
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel

fun <E> JComboBox<E>.whenItemSelected(parentDisposable: Disposable? = null, listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

fun <E> DropDownLink<E>.whenItemSelected(parentDisposable: Disposable? = null, listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

fun <T> ItemSelectable.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  val itemListener = ItemListener { event ->
    if (event.stateChange == ItemEvent.SELECTED) {
      @Suppress("UNCHECKED_CAST")
      listener(event.item as T)
    }
  }
  addItemListener(itemListener)
  parentDisposable?.whenDisposed {
    removeItemListener(itemListener)
  }
}

fun ListModel<*>.whenListChanged(parentDisposable: Disposable? = null, listener: (ListDataEvent) -> Unit) {
  val listDataListener = object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent) = listener(e)
    override fun intervalRemoved(e: ListDataEvent) = listener(e)
    override fun contentsChanged(e: ListDataEvent) = listener(e)
  }
  addListDataListener(listDataListener)
  parentDisposable?.whenDisposed {
    removeListDataListener(listDataListener)
  }
}

fun JTree.whenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  model.whenTreeChanged(parentDisposable, listener)
}

fun TreeModel.whenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  val treeModelListener = TreeModelAdapter.create { event, _ ->
    listener(event)
  }
  addTreeModelListener(treeModelListener)
  parentDisposable?.whenDisposed {
    removeTreeModelListener(treeModelListener)
  }
}

fun TableView<*>.whenTableChanged(parentDisposable: Disposable? = null, listener: (TableModelEvent) -> Unit) {
  tableViewModel.whenTableChanged(parentDisposable, listener)
}

fun TableViewModel<*>.whenTableChanged(parentDisposable: Disposable? = null, listener: (TableModelEvent) -> Unit) {
  val tableModelListener = TableModelListener { event ->
    listener(event)
  }
  addTableModelListener(tableModelListener)
  parentDisposable?.whenDisposed {
    removeTableModelListener(tableModelListener)
  }
}

fun JTextComponent.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  document.whenTextChanged(parentDisposable, listener)
}

fun Document.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  val documentListener = object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      listener(e)
    }
  }
  addDocumentListener(documentListener)
  parentDisposable?.whenDisposed {
    removeDocumentListener(documentListener)
  }
}

fun JTextComponent.whenCaretMoved(parentDisposable: Disposable? = null, listener: (CaretEvent) -> Unit) {
  val caretListener = CaretListener { event ->
    listener(event)
  }
  addCaretListener(caretListener)
  parentDisposable?.whenDisposed {
    removeCaretListener(caretListener)
  }
}

fun JComponent.whenFocusGained(parentDisposable: Disposable? = null, listener: (FocusEvent) -> Unit) {
  val focusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      listener(e)
    }
  }
  addFocusListener(focusListener)
  parentDisposable?.whenDisposed {
    removeFocusListener(focusListener)
  }
}

fun JComponent.onceWhenFocusGained(parentDisposable: Disposable? = null, listener: (FocusEvent) -> Unit) {
  val focusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      removeFocusListener(this)
      listener(e)
    }
  }
  addFocusListener(focusListener)
  parentDisposable?.whenDisposed {
    removeFocusListener(focusListener)
  }
}

fun JComponent.whenMousePressed(parentDisposable: Disposable? = null, listener: (MouseEvent) -> Unit) {
  val mouseListener = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      listener(e)
    }
  }
  addMouseListener(mouseListener)
  parentDisposable?.whenDisposed {
    removeMouseListener(mouseListener)
  }
}

private fun Disposable.whenDisposed(listener: () -> Unit) {
  Disposer.register(this, Disposable { listener() })
}
