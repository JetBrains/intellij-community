// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton

open class ActionLink() : JButton() {
  override fun getUIClassID() = "LinkButtonUI"

  init {
    addPropertyChangeListener("enabled") { if (autoHideOnDisable) isVisible = isEnabled }
  }

  constructor(action: Action) : this() {
    this.action = action
  }

  constructor(text: String, perform: (ActionEvent) -> Unit) : this(text, ActionListener { perform(it) })
  constructor(text: String, listener: ActionListener? = null) : this() {
    this.text = text
    listener?.let { addActionListener(it) }
  }

  var autoHideOnDisable = true
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("autoHideOnDisable", oldValue, newValue)
      isVisible = !newValue || isEnabled
    }

  var visited = true
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("visited", oldValue, newValue)
      repaint()
    }
}
