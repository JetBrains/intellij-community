// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.ui.scale.JBUIScale.scale
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole.HYPERLINK
import javax.swing.*

open class ActionLink() : JButton() {
  override fun getUIClassID(): String = "LinkButtonUI"

  init {
    @Suppress("LeakingThis")
    addPropertyChangeListener("enabled") { if (autoHideOnDisable) isVisible = isEnabled }
  }

  override fun updateUI() {
    // register predefined link implementation if L&F manager is not loaded yet
    UIManager.get(uiClassID) ?: UIManager.put(uiClassID, "com.intellij.ui.components.DefaultLinkButtonUI")
    super.updateUI()
  }

  constructor(action: Action) : this() {
    this.action = action
  }

  constructor(@Nls text: String, perform: (ActionEvent) -> Unit) : this(text, ActionListener { perform(it) })

  @JvmOverloads
  constructor(@Nls text: String, listener: ActionListener? = null) : this() {
    this.text = text
    listener?.let { addActionListener(it) }
  }

  var autoHideOnDisable: Boolean = true
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("autoHideOnDisable", oldValue, newValue)
      isVisible = !newValue || isEnabled
    }

  var visited: Boolean = false
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("visited", oldValue, newValue)
      repaint()
    }

  fun setLinkIcon(): Unit = setIcon(AllIcons.Ide.Link, false)
  fun setContextHelpIcon(): Unit = setIcon(AllIcons.General.ContextHelp, false)
  fun setExternalLinkIcon(): Unit = setIcon(AllIcons.Ide.External_link_arrow, true)
  fun setDropDownLinkIcon(): Unit = setIcon(AllIcons.General.LinkDropTriangle, true)
  fun setIcon(anIcon: Icon, atRight: Boolean) {
    icon = anIcon
    iconTextGap = scale(if (atRight) 1 else 4)
    horizontalTextPosition = if (atRight) SwingConstants.LEADING else SwingConstants.TRAILING
  }

  fun withFont(font: Font): ActionLink {
    setFont(font)
    return this
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleAbstractButton() {
        override fun getAccessibleRole() = HYPERLINK
      }
    }
    return accessibleContext
  }
}
