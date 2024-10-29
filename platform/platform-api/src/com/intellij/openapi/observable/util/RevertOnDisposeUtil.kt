// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.TableViewModel
import java.awt.Component
import java.awt.Container
import java.awt.ItemSelectable
import java.awt.event.*
import java.beans.PropertyChangeListener
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.ListModel
import javax.swing.event.*
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel

fun <T> ObservableMutableProperty<T>.set(value: T, parentDisposable: Disposable? = null) {
  val oldValue = get()
  set(value)
  parentDisposable?.whenDisposed {
    set(oldValue)
  }
}

fun Container.addComponent(component: Component, parentDisposable: Disposable? = null) {
  add(component)
  parentDisposable?.whenDisposed {
    remove(component)
  }
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

fun Document.addDocumentListener(parentDisposable: Disposable? = null, listener: javax.swing.event.DocumentListener) {
  addDocumentListener(listener)
  parentDisposable?.whenDisposed {
    removeDocumentListener(listener)
  }
}

fun EditorTextComponent.addDocumentListener(parentDisposable: Disposable? = null, listener: DocumentListener) {
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

fun JComponent.addMouseHoverListener(parentDisposable: Disposable? = null, listener: HoverListener) {
  when (parentDisposable) {
    null -> listener.addTo(this)
    else -> listener.addTo(this, parentDisposable)
  }
}

fun Component.addKeyListener(parentDisposable: Disposable? = null, listener: KeyListener) {
  addKeyListener(listener)
  parentDisposable?.whenDisposed {
    removeKeyListener(listener)
  }
}

fun Component.addComponentListener(parentDisposable: Disposable? = null, listener: ComponentListener) {
  addComponentListener(listener)
  parentDisposable?.whenDisposed {
    removeComponentListener(listener)
  }
}

fun Component.addPropertyChangeListener(propertyName: String, parentDisposable: Disposable? = null, listener: PropertyChangeListener) {
  addPropertyChangeListener(propertyName, listener)
  parentDisposable?.whenDisposed {
    removePropertyChangeListener(listener)
  }
}

fun ComponentWithBrowseButton<*>.addActionListener(parentDisposable: Disposable? = null, listener: ActionListener) {
  addActionListener(listener)
  parentDisposable?.whenDisposed {
    removeActionListener(listener)
  }
}
