// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.ui.scale.JBUIScale.scale
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.SwingConstants

open class ActionLink() : JButton() {
  enum class Decoration(val icon: Icon, val atRight: Boolean) {
    HELP(AllIcons.General.ContextHelp, false),
    EXTERNAL(AllIcons.Ide.External_link_arrow, true),
    DROP_DOWN(AllIcons.General.LinkDropTriangle, true)
  }

  override fun getUIClassID() = "LinkButtonUI"

  init {
    @Suppress("LeakingThis")
    addPropertyChangeListener("enabled") { if (autoHideOnDisable) isVisible = isEnabled }
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

  var autoHideOnDisable = true
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("autoHideOnDisable", oldValue, newValue)
      isVisible = !newValue || isEnabled
    }

  var visited = false
    set(newValue) {
      val oldValue = field
      if (oldValue == newValue) return
      field = newValue
      firePropertyChange("visited", oldValue, newValue)
      repaint()
    }

  fun setContextHelpIcon() = setIcon(AllIcons.General.ContextHelp, false)
  fun setExternalLinkIcon() = setIcon(AllIcons.Ide.External_link_arrow, true)
  fun setDropDownLinkIcon() = setIcon(AllIcons.General.LinkDropTriangle, true)
  fun setIcon(anIcon: Icon, atRight: Boolean) {
    icon = anIcon
    iconTextGap = scale(if (atRight) 1 else 4)
    horizontalTextPosition = if (atRight) SwingConstants.LEADING else SwingConstants.TRAILING
  }

  fun withIcon(icon: Decoration): ActionLink {
    setIcon(icon.icon, icon.atRight)
    return this
  }

  fun withFont(font: Font): ActionLink {
    setFont(font)
    return this
  }
}
