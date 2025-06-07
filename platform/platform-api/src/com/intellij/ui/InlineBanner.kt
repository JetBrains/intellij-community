// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.FinalLayoutWrapper
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * @author Alexander Lobas
 */
open class InlineBanner private constructor(
  messageText: @Nls String,
  status: EditorNotificationPanel.Status,
  gap: Int,
) : InlineBannerBase(status, gap, messageText) {

  override var status: EditorNotificationPanel.Status = status
    set(value) {
      field = value
      setIcon(value.icon)
      super.status = value
    }

  private val myIcon = JBLabel()
  private val myButtonPanel: JPanel
  private val myCloseButton: JComponent
  private var myGearButton: JComponent? = null
  private var myCloseAction: Runnable? = null
  private val myActionPanel: JPanel

  @Suppress("unused")
  constructor(status: EditorNotificationPanel.Status) : this(
    messageText = "",
    status = status,
  )

  @JvmOverloads
  constructor(
    messageText: @Nls String = "",
    status: EditorNotificationPanel.Status = EditorNotificationPanel.Status.Info,
  ) : this(messageText, status, JBUI.scale(8))

  init {
    myCloseButton = createInplaceButton(IdeBundle.message("editor.banner.close.tooltip"), AllIcons.General.Close) {
      close()
    }

    layout = object : BorderLayout(gap, gap) {
      @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
      override fun addLayoutComponent(name: String?, comp: Component) {
        if (comp !== myCloseButton && comp !== myGearButton) {
          super.addLayoutComponent(name, comp)
        }
      }

      override fun layoutContainer(target: Container) {
        super.layoutContainer(target)

        val y = JBUI.scale(7)
        var x = target.width - JBUI.scale(7)

        if (myCloseButton.isVisible) {
          val size = myCloseButton.preferredSize
          x -= size.width
          myCloseButton.setBounds(x, y, size.width, size.height)
          x -= JBUI.scale(2)
        }
        if (myGearButton != null) {
          val size = myGearButton!!.preferredSize
          x -= size.width
          myGearButton!!.setBounds(x, y, size.width, size.height)
        }
      }
    }

    iconPanel.add(myIcon, BorderLayout.NORTH)
    setIcon(status.icon)
    add(iconPanel, BorderLayout.WEST)
    add(centerPanel)

    add(myCloseButton)

    val titlePanel = JPanel(BorderLayout())
    titlePanel.isOpaque = isOpaque
    titlePanel.background = background
    titlePanel.add(message)
    centerPanel.add(titlePanel)

    myButtonPanel = JPanel()
    myButtonPanel.isOpaque = isOpaque
    updateButtonsSize()
    titlePanel.add(myButtonPanel, BorderLayout.EAST)

    myActionPanel = JPanel(DropDownActionLayout(HorizontalLayout(JBUI.scale(16))))
    myActionPanel.isOpaque = isOpaque
    myActionPanel.isVisible = false
    myActionPanel.add(DropDownAction())
    centerPanel.add(myActionPanel)
  }

  private fun createInplaceButton(tooltip: @Nls String, icon: Icon, listener: ActionListener): JComponent {
    val button = object : InplaceButton(tooltip, IconButton(null, icon, null, null), listener) {
      private val myTimer = Timer(300) { stopClickTimer() }
      private var myClick = false

      private fun startClickTimer() {
        myClick = true
        repaint()
        myTimer.start()
      }

      private fun stopClickTimer() {
        myClick = false
        repaint()
        myTimer.stop()
      }

      override fun doClick(e: MouseEvent) {
        startClickTimer()
        super.doClick(e)
      }

      override fun paintHover(g: Graphics) {
        paintHover(g, if (myClick) JBUI.CurrentTheme.InlineBanner.PRESSED_BACKGROUND else JBUI.CurrentTheme.InlineBanner.HOVER_BACKGROUND)
      }
    }
    button.preferredSize = JBDimension(26, 26)
    return button
  }

  fun setMessage(text: @Nls String): InlineBanner {
    messageText = text
    return this
  }

  fun setIcon(icon: Icon?): InlineBanner {
    myIcon.icon = icon
    myIcon.isVisible = icon != null
    iconPanel.isVisible = icon != null
    return this
  }

  @ApiStatus.Internal
  // similar to what EditorNotificationPanel does
  fun createActionLabel(text: @NlsContexts.Label String, action: Runnable): JComponent {
    return addActionImpl(text, action)
  }

  private fun addActionImpl(name: @Nls String, action: Runnable): LinkLabel<Runnable> {
    return addAction(name, null, action)
  }

  fun addAction(name: @Nls String, icon: Icon?, action: Runnable): LinkLabel<Runnable> {
    myActionPanel.isVisible = true
    val label = object : LinkLabel<Runnable>(name, icon, { _, action -> action.run() }, action) {
      override fun getTextColor() = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
    myActionPanel.add(label, myActionPanel.componentCount - 1)
    return label
  }

  fun addAction(name: @Nls String, action: Runnable): InlineBanner {
    addActionImpl(name, action)

    return this
  }

  @ApiStatus.Internal
  fun removeAllActions() {
    myActionPanel.removeAll()
    myActionPanel.add(DropDownAction())
  }

  fun showCloseButton(visible: Boolean): InlineBanner {
    myCloseButton.isVisible = visible
    updateButtonsSize()
    return this
  }

  fun setCloseAction(action: Runnable): InlineBanner {
    myCloseAction = action
    return this
  }

  fun close() {
    myCloseAction?.run()
    removeFromParent()
  }

  open fun removeFromParent() {
    val parent = parent
    parent?.remove(this)
    parent?.doLayout()
    parent?.revalidate()
    parent?.repaint()
  }

  fun setGearAction(tooltip: @Nls String, action: Runnable): InlineBanner {
    if (myGearButton != null) {
      remove(myGearButton)
    }

    myGearButton = createInplaceButton(tooltip, AllIcons.General.GearPlain) {
      action.run()
    }
    add(myGearButton)
    updateButtonsSize()

    return this
  }

  private fun updateButtonsSize() {
    var buttons = 0
    if (myCloseButton.isVisible) {
      buttons++
    }
    if (myGearButton != null) {
      buttons++
    }
    myButtonPanel.preferredSize = JBDimension(buttons * 22, 16)
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
      val size = actions.size
      var collapseIndex = size - 1

      if (size < 4) {
        myDropDownAction.isVisible = false
        for (action in actions) {
          action.isVisible = true
        }
      }
      else {
        actions[0].isVisible = true
        actions[1].isVisible = true
        collapseIndex = 1
        myDropDownAction.isVisible = true
        for (i in 2 until size) {
          actions[i].isVisible = false
        }
      }

      layout.layoutContainer(parent)

      if (parent.preferredSize.width > width) {
        myDropDownAction.isVisible = true
        actions[collapseIndex].isVisible = false
        collapseIndex -= 1
        layout.layoutContainer(parent)

        while (parent.preferredSize.width > width && collapseIndex >= 0 && collapseIndex < size) {
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
