// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerUiUtil")
@file:Suppress("unused")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.table.TableView
import com.intellij.util.ui.TableViewModel
import com.intellij.util.ui.tree.TreeModelAdapter
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component
import java.awt.ItemSelectable
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel

fun <T> JComboBox<T>.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

fun <T> DropDownLink<T>.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

fun <T> ItemSelectable.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  whenStateChanged(parentDisposable) { event ->
    if (event.stateChange == ItemEvent.SELECTED) {
      @Suppress("UNCHECKED_CAST")
      listener(event.item as T)
    }
  }
}

fun ItemSelectable.whenStateChanged(parentDisposable: Disposable? = null, listener: (ItemEvent) -> Unit) {
  addItemListener(parentDisposable, ItemListener { event ->
    listener(event)
  })
}

fun JComboBox<*>.whenPopupMenuWillBecomeInvisible(parentDisposable: Disposable? = null, listener: (PopupMenuEvent) -> Unit) {
  addPopupMenuListener(parentDisposable, object : PopupMenuListenerAdapter() {
    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = listener(e)
  })
}

fun ListModel<*>.whenListChanged(parentDisposable: Disposable? = null, listener: (ListDataEvent) -> Unit) {
  addListDataListener(parentDisposable, object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent) = listener(e)
    override fun intervalRemoved(e: ListDataEvent) = listener(e)
    override fun contentsChanged(e: ListDataEvent) = listener(e)
  })
}

fun JTree.whenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  model.whenTreeChanged(parentDisposable, listener)
}

fun TreeModel.whenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  addTreeModelListener(parentDisposable, TreeModelAdapter.create { event, _ ->
    listener(event)
  })
}

fun TableView<*>.whenTableChanged(parentDisposable: Disposable? = null, listener: (TableModelEvent) -> Unit) {
  tableViewModel.whenTableChanged(parentDisposable, listener)
}

fun TableViewModel<*>.whenTableChanged(parentDisposable: Disposable? = null, listener: (TableModelEvent) -> Unit) {
  addTableModelListener(parentDisposable, TableModelListener { event ->
    listener(event)
  })
}

fun TextFieldWithBrowseButton.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  textField.whenTextChanged(parentDisposable, listener)
}

fun JTextComponent.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  document.whenTextChanged(parentDisposable, listener)
}

fun Document.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  addDocumentListener(parentDisposable, object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) = listener(e)
  })
}

fun JTextComponent.whenCaretMoved(parentDisposable: Disposable? = null, listener: (CaretEvent) -> Unit) {
  addCaretListener(parentDisposable, CaretListener { event ->
    listener(event)
  })
}

fun Component.whenFocusGained(parentDisposable: Disposable? = null, listener: (FocusEvent) -> Unit) {
  addFocusListener(parentDisposable, object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) = listener(e)
  })
}

fun Component.whenFocusLost(parentDisposable: Disposable? = null, listener: (FocusEvent) -> Unit) {
  addFocusListener(parentDisposable, object : FocusAdapter() {
    override fun focusLost(e: FocusEvent) = listener(e)
  })
}

fun Component.onceWhenFocusGained(parentDisposable: Disposable? = null, listener: (FocusEvent) -> Unit) {
  addFocusListener(parentDisposable, object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      removeFocusListener(this)
      listener(e)
    }
  })
}

fun Component.whenMousePressed(parentDisposable: Disposable? = null, listener: (MouseEvent) -> Unit) {
  addMouseListener(parentDisposable, object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) = listener(e)
  })
}

fun Component.whenMouseReleased(parentDisposable: Disposable? = null, listener: (MouseEvent) -> Unit) {
  addMouseListener(parentDisposable, object : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) = listener(e)
  })
}

fun Component.whenKeyTyped(parentDisposable: Disposable? = null, listener: (KeyEvent) -> Unit) {
  addKeyListener(parentDisposable, object : KeyAdapter() {
    override fun keyTyped(e: KeyEvent) = listener(e)
  })
}

fun Component.whenKeyPressed(parentDisposable: Disposable? = null, listener: (KeyEvent) -> Unit) {
  addKeyListener(parentDisposable, object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) = listener(e)
  })
}

fun Component.whenKeyReleased(parentDisposable: Disposable? = null, listener: (KeyEvent) -> Unit) {
  addKeyListener(parentDisposable, object : KeyAdapter() {
    override fun keyReleased(e: KeyEvent) = listener(e)
  })
}

fun ItemSelectable.addItemListener(parentDisposable: Disposable? = null, listener: ItemListener) {
  addItemListener(listener)
  parentDisposable?.whenDisposed {
    removeItemListener(listener)
  }
}

fun JComboBox<*>.addPopupMenuListener(parentDisposable: Disposable? = null, listener: PopupMenuListener) {
  addPopupMenuListener(listener)
  parentDisposable?.whenDisposed {
    removePopupMenuListener(listener)
  }
}

fun ListModel<*>.addListDataListener(parentDisposable: Disposable? = null, listener: ListDataListener) {
  addListDataListener(listener)
  parentDisposable?.whenDisposed {
    removeListDataListener(listener)
  }
}

fun TreeModel.addTreeModelListener(parentDisposable: Disposable? = null, listener: TreeModelListener) {
  addTreeModelListener(listener)
  parentDisposable?.whenDisposed {
    removeTreeModelListener(listener)
  }
}

fun TableViewModel<*>.addTableModelListener(parentDisposable: Disposable? = null, listener: TableModelListener) {
  addTableModelListener(listener)
  parentDisposable?.whenDisposed {
    removeTableModelListener(listener)
  }
}

fun Document.addDocumentListener(parentDisposable: Disposable? = null, listener: DocumentListener) {
  addDocumentListener(listener)
  parentDisposable?.whenDisposed {
    removeDocumentListener(listener)
  }
}

fun JTextComponent.addCaretListener(parentDisposable: Disposable? = null, listener: CaretListener) {
  addCaretListener(listener)
  parentDisposable?.whenDisposed {
    removeCaretListener(listener)
  }
}

fun Component.addFocusListener(parentDisposable: Disposable? = null, listener: FocusListener) {
  addFocusListener(listener)
  parentDisposable?.whenDisposed {
    removeFocusListener(listener)
  }
}

fun Component.addMouseListener(parentDisposable: Disposable? = null, listener: MouseListener) {
  addMouseListener(listener)
  parentDisposable?.whenDisposed {
    removeMouseListener(listener)
  }
}

fun Component.addKeyListener(parentDisposable: Disposable? = null, listener: KeyListener) {
  addKeyListener(listener)
  parentDisposable?.whenDisposed {
    removeKeyListener(listener)
  }
}

@Experimental
fun <T> JComboBox<T>.whenItemSelectedFromUi(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  whenPopupMenuWillBecomeInvisible(parentDisposable) {
    invokeLater(ModalityState.stateForComponent(this)) {
      selectedItem?.let {
        @Suppress("UNCHECKED_CAST")
        listener(it as T)
      }
    }
  }
}

@Experimental
fun TextFieldWithBrowseButton.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  textField.whenTextChangedFromUi(parentDisposable, listener)
}

@Experimental
fun JTextComponent.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  whenKeyReleased(parentDisposable) {
    invokeLater(ModalityState.stateForComponent(this)) {
      listener(text)
    }
  }
}

@Experimental
fun JCheckBox.whenStateChangedFromUi(parentDisposable: Disposable? = null, listener: (Boolean) -> Unit) {
  whenMouseReleased(parentDisposable) {
    invokeLater(ModalityState.stateForComponent(this)) {
      listener(isSelected)
    }
  }
}
