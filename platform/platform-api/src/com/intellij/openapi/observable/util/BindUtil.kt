// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BindUtil")

package com.intellij.openapi.observable.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.observable.properties.ObservableBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.ui.getTreePath
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Skips [action] execution if this or other block is executing with [this] "atomic".
 * Needs to break the recursion locally.
 */
inline fun AtomicBoolean.lockOrSkip(action: () -> Unit) {
  if (!compareAndSet(false, true)) return
  try {
    action()
  }
  finally {
    set(false)
  }
}

fun <T> ObservableMutableProperty<T>.bind(property: ObservableProperty<T>) {
  set(property.get())
  property.afterChange {
    set(it)
  }
}

/**
 * Binds two observable properties.
 * All changes will be transferred from current property to [property] and back.
 */
fun <T> ObservableMutableProperty<T>.bind(property: ObservableMutableProperty<T>) {
  val mutex = AtomicBoolean()
  set(property.get())
  property.afterChange {
    mutex.lockOrSkip {
      set(it)
    }
  }
  afterChange {
    mutex.lockOrSkip {
      property.set(it)
    }
  }
}

fun <P : ObservableMutableProperty<Int>> P.bindIntStorage(propertyName: String): P = apply {
  toStringIntProperty()
    .bindStorage(propertyName)
}

fun <P : ObservableMutableProperty<Boolean>> P.bindBooleanStorage(propertyName: String): P = apply {
  toStringBooleanProperty()
    .bindStorage(propertyName)
}

inline fun <reified T : Enum<T>, P : ObservableMutableProperty<T>> P.bindEnumStorage(propertyName: String): P = apply {
  toStringEnumProperty()
    .bindStorage(propertyName)
}

fun <P : ObservableMutableProperty<String>> P.bindStorage(propertyName: String): P = apply {
  val properties = PropertiesComponent.getInstance()
  val value = properties.getValue(propertyName)
  if (value != null) {
    set(value)
  }
  afterChange {
    properties.setValue(propertyName, it)
  }
}

fun <C : Component> C.bindEnabled(property: ObservableProperty<Boolean>): C = apply {
  UIUtil.setEnabledRecursively(this, property.get())
  property.afterChange { UIUtil.setEnabledRecursively(this, it) }
}

fun <C : Component> C.bindVisible(property: ObservableProperty<Boolean>): C = apply {
  isVisible = property.get()
  property.afterChange { isVisible = it }
}

fun <C : ComponentWithEmptyText> C.bindEmptyText(property: ObservableProperty<@NlsContexts.StatusText String>): C = apply {
  emptyText.text = property.get()
  emptyText.bind(property)
}

fun <C : TextFieldWithBrowseButton> C.bindEmptyText(property: ObservableProperty<@NlsContexts.StatusText String>): C = apply {
  emptyText.text = property.get()
  emptyText.bind(property)
}

fun <T, C : JComboBox<T>> C.bind(property: ObservableProperty<T>): C = apply {
  selectedItem = property.get()
  property.afterChange {
    selectedItem = it
  }
}

/**
 * Binds selected item of [this] combobox and [property] value.
 * Note: Ignores proportion of selected item for deselected event.
 * @see java.awt.event.ItemEvent.DESELECTED
 */
fun <T, C : JComboBox<T>> C.bind(property: ObservableMutableProperty<T>): C = apply {
  selectedItem = property.get()
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      selectedItem = it
    }
  }
  whenItemSelected {
    mutex.lockOrSkip {
      property.set(it)
    }
  }
}

fun <T, C : DropDownLink<T>> C.bind(property: ObservableProperty<T>): C = apply {
  selectedItem = property.get()
  property.afterChange {
    selectedItem = it
  }
}

fun <T, C : DropDownLink<T>> C.bind(property: ObservableMutableProperty<T>): C = apply {
  selectedItem = property.get()
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      selectedItem = it
    }
  }
  whenItemSelected {
    mutex.lockOrSkip {
      property.set(it)
    }
  }
}

fun <T, C : JList<T>> C.bind(property: ObservableProperty<T?>): C = apply {
  setSelectedValue(property.get(), true)
  property.afterChange {
    setSelectedValue(it, true)
  }
}

fun <T, C : JList<T>> C.bind(property: ObservableMutableProperty<T?>): C = apply {
  setSelectedValue(property.get(), true)
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      setSelectedValue(it, true)
    }
  }
  addListSelectionListener {
    mutex.lockOrSkip {
      if (!it.valueIsAdjusting) {
        property.set(selectedValue)
      }
    }
  }
}

fun <T, C : JTree> C.bind(property: ObservableProperty<T?>): C = apply {
  selectionPath = model.getTreePath(property.get())
  property.afterChange {
    selectionPath = model.getTreePath(it)
  }
}

fun <T, C : JTree> C.bind(property: ObservableMutableProperty<T?>): C = apply {
  selectionPath = model.getTreePath(property.get())
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      selectionPath = model.getTreePath(it)
    }
  }
  addTreeSelectionListener {
    mutex.lockOrSkip {
      val node = lastSelectedPathComponent as? DefaultMutableTreeNode
      @Suppress("UNCHECKED_CAST")
      property.set(node?.userObject as T?)
    }
  }
}

fun <C : StatusText> C.bind(property: ObservableProperty<@NlsContexts.StatusText String>): C = apply {
  text = property.get()
  property.afterChange {
    text = it
  }
}

fun <C : JLabel> C.bind(property: ObservableProperty<@NlsContexts.Label String>): C = apply {
  text = property.get()
  property.afterChange {
    text = it
  }
}

fun <C : JToggleButton> C.bind(property: ObservableProperty<Boolean>): C = apply {
  isSelected = property.get()
  property.afterChange {
    isSelected = it
  }
}

fun <C : JToggleButton> C.bind(property: ObservableMutableProperty<Boolean>): C = apply {
  isSelected = property.get()
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      isSelected = it
    }
  }
  addItemListener {
    mutex.lockOrSkip {
      property.set(isSelected)
    }
  }
}

fun <C : ThreeStateCheckBox> C.bind(property: ObservableProperty<ThreeStateCheckBox.State>): C = apply {
  state = property.get()
  property.afterChange {
    state = it
  }
}

fun <C : ThreeStateCheckBox> C.bind(property: ObservableMutableProperty<ThreeStateCheckBox.State>): C = apply {
  state = property.get()
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      state = it
    }
  }
  addItemListener {
    mutex.lockOrSkip {
      property.set(state)
    }
  }
}

fun <C : TextFieldWithBrowseButton> C.bind(property: ObservableProperty<String>): C = apply {
  textField.bind(property)
}

fun <C : TextFieldWithBrowseButton> C.bind(property: ObservableMutableProperty<String>): C = apply {
  textField.bind(property)
}

fun <C : JTextComponent> C.bind(property: ObservableProperty<String>): C = apply {
  text = property.get()
  property.afterChange {
    text = it
  }
}

fun <C : JTextComponent> C.bind(property: ObservableMutableProperty<String>): C = apply {
  text = property.get()
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      text = it
    }
  }
  whenTextChanged {
    mutex.lockOrSkip {
      property.set(text)
    }
  }
}

fun <C : JBLoadingPanel> C.bindLoading(property: ObservableBooleanProperty): C = apply {
  if (property.get()) {
    startLoading()
  }
  else {
    stopLoading()
  }
  property.afterSet { startLoading() }
  property.afterReset { stopLoading() }
}

fun <C : JBLoadingPanel> C.bindLoadingText(property: ObservableProperty<@Nls String>): C = apply {
  setLoadingText(property.get())
  property.afterChange { setLoadingText(it) }
}
