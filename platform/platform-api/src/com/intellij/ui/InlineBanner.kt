// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.FinalLayoutWrapper
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

/**
 * @author Alexander Lobas
 */
class InlineBanner(background: Color, private var myBorderColor: Color, icon: Icon?) : JBPanel<InlineBanner>() {
  private val myIconPanel: JPanel
  private val myIcon = JBLabel()
  private val myMessage = JEditorPane()
  private val myCloseButton: JComponent
  private var myCloseAction: Runnable? = null
  private val myActionPanel: JPanel

  constructor(status: EditorNotificationPanel.Status) : this(status.background, status.border, status.icon)

  constructor() : this(EditorNotificationPanel.Status.Info)

  constructor(text: @Nls String, status: EditorNotificationPanel.Status) : this(status) {
    setMessage(text)
  }

  constructor(text: @Nls String) : this() {
    setMessage(text)
  }

  init {
    val gap = JBUI.scale(8)

    layout = BorderLayout(gap, gap)
    border = JBUI.Borders.empty(12)
    isOpaque = true
    this.background = background

    myIconPanel = JPanel(BorderLayout())
    myIconPanel.isOpaque = false
    myIconPanel.add(myIcon, BorderLayout.NORTH)
    setIcon(icon)
    add(myIconPanel, BorderLayout.WEST)

    val centerPanel = JPanel(VerticalLayout(gap))
    centerPanel.isOpaque = false
    add(centerPanel)

    myMessage.isEditable = false
    myMessage.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    myMessage.contentType = "text/html"
    myMessage.isOpaque = false
    myMessage.border = null
    myMessage.isEditable = false
    if (myMessage.caret != null) {
      myMessage.caretPosition = 0
    }

    myCloseButton = InplaceButton(IconButton(null, AllIcons.Windows.CloseActive, null, null)) {
      close()
    }
    myCloseButton.preferredSize = JBDimension(22, 22)

    val titlePanel = JPanel(BorderLayout())
    titlePanel.isOpaque = false
    titlePanel.add(myMessage)
    centerPanel.add(titlePanel)

    val buttonPanel = JPanel(BorderLayout())
    buttonPanel.isOpaque = false
    buttonPanel.add(myCloseButton, BorderLayout.NORTH)
    titlePanel.add(buttonPanel, BorderLayout.EAST)

    myActionPanel = JPanel(DropDownActionLayout(HorizontalLayout(JBUIScale.scale(16))))
    myActionPanel.isOpaque = false
    myActionPanel.isVisible = false
    myActionPanel.add(DropDownAction())
    centerPanel.add(myActionPanel)
  }

  fun setMessage(text: @Nls String): InlineBanner {
    myMessage.text = text
    if (myMessage.caret != null) {
      myMessage.caretPosition = 0
    }
    return this
  }

  fun setType(status: EditorNotificationPanel.Status): InlineBanner {
    return setPresentation(status.background, status.border, status.icon)
  }

  fun setIcon(icon: Icon?): InlineBanner {
    myIcon.icon = icon
    myIcon.isVisible = icon != null
    myIconPanel.isVisible = icon != null
    return this
  }

  fun setPresentation(background: Color, border: Color, icon: Icon?): InlineBanner {
    this.background = background
    myBorderColor = border
    setIcon(icon)
    repaint()
    return this
  }

  fun addAction(name: @Nls String, action: Runnable): InlineBanner {
    myActionPanel.isVisible = true
    myActionPanel.add(object : LinkLabel<Runnable>(name, null, { _, action -> action.run() }, action) {
      override fun getTextColor() = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }, myActionPanel.componentCount - 1)
    return this
  }

  fun showCloseButton(visible: Boolean): InlineBanner {
    myCloseButton.isVisible = visible
    return this
  }

  fun setCloseAction(action: Runnable): InlineBanner {
    myCloseAction = action
    return this
  }

  fun close() {
    myCloseAction?.run()
    isVisible = false
    val parent = parent
    parent?.remove(this)
    parent?.doLayout()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val config = GraphicsUtil.setupAAPainting(g)
    val cornerRadius = JBUI.scale(12)
    g.color = background
    g.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g.color = myBorderColor
    g.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    config.restore()
  }

  private class DropDownActionLayout(layout: LayoutManager2) : FinalLayoutWrapper(layout) {
    val actions = ArrayList<LinkLabel<Runnable>>()
    private lateinit var myDropDownAction: DropDownAction

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
      super.addLayoutComponent(comp, constraints)
      add(comp)
    }

    override fun addLayoutComponent(name: String?, comp: Component) {
      super.addLayoutComponent(name, comp)
      add(comp)
    }

    private fun add(component: Component) {
      if (component is DropDownAction) {
        myDropDownAction = component
      }
      else if (component is LinkLabel<*>) {
        @Suppress("UNCHECKED_CAST")
        actions.add(component as LinkLabel<Runnable>)
        layout.removeLayoutComponent(myDropDownAction)
        layout.addLayoutComponent(myDropDownAction, null)
      }
    }

    override fun layoutContainer(parent: Container) {
      val width = parent.width

      myDropDownAction.isVisible = false
      for (action in actions) {
        action.isVisible = true
      }
      layout.layoutContainer(parent)

      var collapseIndex = actions.size - 1

      if (parent.preferredSize.width > width) {
        myDropDownAction.isVisible = true
        actions[collapseIndex].isVisible = false
        collapseIndex -= 1
        layout.layoutContainer(parent)

        while (parent.preferredSize.width > width && collapseIndex >= 0 && collapseIndex < actions.size) {
          actions[collapseIndex].isVisible = false
          collapseIndex -= 1
          layout.layoutContainer(parent)
        }
      }
    }
  }

  private inner class DropDownAction : LinkLabel<Runnable>() {
    init {
      horizontalTextPosition = LEADING
      iconTextGap = JBUI.scale(1)

      icon = object : Icon {
        private val icon = AllIcons.General.LinkDropTriangle
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
          icon.paintIcon(c, g, x, y + 1)
        }

        override fun getIconWidth() = icon.iconWidth

        override fun getIconHeight() = icon.iconHeight
      }

      setListener(LinkListener { link, _ ->
        if (link.isShowing()) {
          val group = DefaultActionGroup()
          val layout = link.parent.layout as DropDownActionLayout

          for (action in layout.actions) {
            if (!action.isVisible) {
              group.add(object : DumbAwareAction(action.text) {
                override fun actionPerformed(e: AnActionEvent) {
                  action.linkData.run()
                }
              })
            }
          }

          val menu = ActionManager.getInstance().createActionPopupMenu("InlineBanner", group)
          menu.getComponent().show(link, JBUIScale.scale(-10), link.height + JBUIScale.scale(2))
        }
      }, null)

      text = IdeCoreBundle.message("notifications.action.more")
      isVisible = false
    }
  }
}