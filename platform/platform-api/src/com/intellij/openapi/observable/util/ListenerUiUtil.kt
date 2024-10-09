// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerUiUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.table.TableView
import com.intellij.util.ui.TableViewModel
import com.intellij.util.ui.tree.TreeModelAdapter
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component
import java.awt.Dimension
import java.awt.ItemSelectable
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel
import com.intellij.openapi.editor.event.DocumentEvent as EditorDocumentEvent
import com.intellij.openapi.editor.event.DocumentListener as EditorDocumentListener

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

fun JTree.onceWhenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  model.onceWhenTreeChanged(parentDisposable, listener)
}

fun TreeModel.whenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  addTreeModelListener(parentDisposable, TreeModelAdapter.create { event, _ ->
    listener(event)
  })
}

fun TreeModel.onceWhenTreeChanged(parentDisposable: Disposable? = null, listener: (TreeModelEvent) -> Unit) {
  addTreeModelListener(parentDisposable, object : TreeModelAdapter() {
    override fun process(event: TreeModelEvent, type: EventType) {
      removeTreeModelListener(this)
      listener(event)
    }
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

@Deprecated("Changed listener argument type from DocumentEvent to String.", level = DeprecationLevel.HIDDEN)
fun TextFieldWithBrowseButton.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  textField.document.whenDocumentChanged(parentDisposable, listener)
}

@Deprecated("Changed listener argument type from DocumentEvent to String.", level = DeprecationLevel.HIDDEN)
fun SearchTextField.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  textEditor.document.whenDocumentChanged(parentDisposable, listener)
}

@Deprecated("Changed listener argument type from DocumentEvent to String.", level = DeprecationLevel.HIDDEN)
fun JTextComponent.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  document.whenDocumentChanged(parentDisposable, listener)
}

@Deprecated("Changed listener argument type from DocumentEvent to String.", level = DeprecationLevel.HIDDEN)
fun Document.whenTextChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  whenDocumentChanged(parentDisposable, listener)
}

fun Document.whenDocumentChanged(parentDisposable: Disposable? = null, listener: (DocumentEvent) -> Unit) {
  addDocumentListener(parentDisposable, object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) = listener(e)
  })
}

fun EditorTextComponent.whenDocumentChanged(parentDisposable: Disposable? = null, listener: (EditorDocumentEvent) -> Unit) {
  addDocumentListener(parentDisposable, object : EditorDocumentListener {
    override fun documentChanged(event: EditorDocumentEvent) {
      listener(event)
    }
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

fun JComponent.whenMouseMoved(parentDisposable: Disposable? = null, listener: (MouseEvent) -> Unit) {
  addMouseHoverListener(parentDisposable, object : HoverListener() {
    override fun mouseEntered(component: Component, x: Int, y: Int) = Unit
    override fun mouseExited(component: Component) = Unit
    override fun mouseMoved(component: Component, x: Int, y: Int) {
      listener.invoke(MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false))
    }
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

fun JComponent.whenSizeChanged(parentDisposable: Disposable? = null, listener: (Dimension) -> Unit) {
  addComponentListener(parentDisposable, object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) = listener(size)
  })
}

inline fun <reified T> Component.whenPropertyChanged(
  propertyName: String,
  parentDisposable: Disposable? = null,
  crossinline listener: (T) -> Unit
) {
  addPropertyChangeListener(propertyName, parentDisposable) { event ->
    listener(event.newValue as T)
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
fun SearchTextField.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  textEditor.whenTextChangedFromUi(parentDisposable, listener)
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
