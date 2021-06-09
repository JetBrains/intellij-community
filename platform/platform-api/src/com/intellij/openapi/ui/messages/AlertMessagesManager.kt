// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.InplaceButton
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.MacMessages
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.Border

/**
 * @author Alexander Lobas
 */

@Service
class AlertMessagesManager : MacMessages() {
  companion object {
    @JvmStatic
    fun isEnabled(): Boolean = Registry.`is`("ide.message.dialogs.as.swing.alert", true)

    @JvmStatic
    fun instance(): AlertMessagesManager = ApplicationManager.getApplication().getService(AlertMessagesManager::class.java)
  }

  override fun showYesNoCancelDialog(title: String,
                                     message: String,
                                     yesText: String,
                                     noText: String,
                                     cancelText: String,
                                     window: Window?,
                                     doNotAskOption: DialogWrapper.DoNotAskOption?,
                                     icon: Icon?,
                                     helpId: String?): Int {
    return showMessageDialog(null, window, message, title, arrayOf(yesText, noText, cancelText), 0, -1, icon, doNotAskOption, helpId)
  }

  override fun showOkMessageDialog(title: String, message: String?, okText: String, window: Window?) {
    showMessageDialog(null, window, message, title, arrayOf(okText), 0, -1, null, null, null)
  }

  override fun showYesNoDialog(title: String,
                               message: String,
                               yesText: String,
                               noText: String,
                               window: Window?,
                               doNotAskDialogOption: DialogWrapper.DoNotAskOption?,
                               icon: Icon?,
                               helpId: String?): Boolean {
    return showMessageDialog(null, window, message, title, arrayOf(yesText, noText), 0, -1, null, doNotAskDialogOption,
                             null) == Messages.YES
  }

  override fun showErrorDialog(title: String, message: String?, okButton: String, window: Window?) {
    showMessageDialog(null, window, message, title, arrayOf(okButton), 0, -1, Messages.getErrorIcon(), null, null)
  }

  override fun showMessageDialog(title: String,
                                 message: String?,
                                 buttons: Array<String>,
                                 window: Window?,
                                 defaultOptionIndex: Int,
                                 focusedOptionIndex: Int,
                                 doNotAskOption: DialogWrapper.DoNotAskOption?,
                                 icon: Icon?,
                                 helpId: String?): Int {
    return showMessageDialog(null, window, message, title, buttons, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption, helpId)
  }

  fun showMessageDialog(project: Project?,
                        parentComponent: Component?,
                        @NlsContexts.DialogMessage message: String?,
                        @NlsContexts.DialogTitle title: String?,
                        options: Array<String>,
                        defaultOptionIndex: Int,
                        focusedOptionIndex: Int,
                        icon: Icon?,
                        doNotAskOption: DialogWrapper.DoNotAskOption?,
                        helpId: String?): Int {
    val dialog = AlertDialog(project, parentComponent, message, title, options, defaultOptionIndex, focusedOptionIndex, getIcon(icon),
                             doNotAskOption, helpId)
    dialog.show()
    return dialog.exitCode
  }
}

private fun getIcon(icon: Icon?): Icon {
  if (icon == UIUtil.getInformationIcon() || icon == null) {
    return AllIcons.General.InformationDialog
  }
  if (icon == UIUtil.getQuestionIcon()) {
    return AllIcons.General.QuestionDialog
  }
  if (icon == UIUtil.getWarningIcon()) {
    return AllIcons.General.WarningDialog
  }
  if (icon == UIUtil.getErrorIcon()) {
    return AllIcons.General.ErrorDialog
  }
  return icon
}

private class AlertDialog(project: Project?,
                          parentComponent: Component?,
                          @NlsContexts.DialogMessage val myMessage: String?,
                          @NlsContexts.DialogTitle val myTitle: String?,
                          val myOptions: Array<String>,
                          val myDefaultOptionIndex: Int,
                          val myFocusedOptionIndex: Int,
                          val myIcon: Icon,
                          doNotAskOption: DoNotAskOption?,
                          val myHelpId: String?) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE, false) {

  private val myTitleComponent = SystemInfoRt.isMac || !Registry.`is`("ide.message.dialogs.as.swing.alert.show.title.bar", false)

  private val myIconComponent = JLabel(myIcon)
  private val mySouthPanel = JPanel(BorderLayout(0, JBUI.scale(20)))
  private val myButtonsPanel = JPanel()
  private val myCloseButton: JComponent?
  private val myButtons = ArrayList<JButton>()
  private var myHelpButton: JButton? = null

  init {
    setUndecorated(myTitleComponent)
    title = myTitle
    setDoNotAskOption(doNotAskOption)

    if (myTitleComponent && !SystemInfoRt.isMac) {
      myCloseButton = InplaceButton(IconButton(null, AllIcons.Ide.Notification.Close, AllIcons.Ide.Notification.CloseHover, null)) {
        doCancelAction()
      }
    }
    else {
      myCloseButton = null
    }

    if (SystemInfoRt.isMac) {
      setInitialLocationCallback {
        val rootPane = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
        val location = rootPane.locationOnScreen
        Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
      }
    }

    init()

    if (myHelpButton != null) {
      val helpButton = myHelpButton!!
      helpButton.parent.remove(helpButton)
      contentPane.add(helpButton, 0)
    }

    if (myCloseButton != null) {
      contentPane.add(myCloseButton, 0)
    }

    if (myTitleComponent) {
      object : MouseDragHelper<JComponent>(myDisposable, contentPane as JComponent) {
        var myLocation: Point? = null

        override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
          val target = dragComponent.findComponentAt(dragComponentPoint)
          return target == null || target == dragComponent || target is JPanel
        }

        override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
          if (myLocation == null) {
            myLocation = window.location
          }
          window.location = Point(myLocation!!.x + dragToScreenPoint.x - startScreenPoint.x,
                                  myLocation!!.y + dragToScreenPoint.y - startScreenPoint.y)
        }

        override fun processDragCancel() {
          myLocation = null
        }

        override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
          myLocation = null
        }

        override fun processDragOutFinish(event: MouseEvent) {
          myLocation = null
        }

        override fun processDragOutCancel() {
          myLocation = null
        }

        override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
          super.processDragOut(event, dragToScreenPoint, startScreenPoint, justStarted)
          myLocation = null
        }
      }.start()
    }
  }

  override fun getInitialSize(): Dimension = Dimension(
    JBUI.scale(if (StringUtil.length(myMessage) < 130 || myButtonsPanel.preferredSize.width < JBUI.scale(300)) 370 else 440),
    preferredSize.height)

  override fun createRootLayout(): LayoutManager {
    return object : BorderLayout() {
      override fun addLayoutComponent(name: String?, comp: Component) {
        if (comp != myCloseButton && comp != myHelpButton) {
          super.addLayoutComponent(name, comp)
        }
      }

      override fun layoutContainer(target: Container) {
        super.layoutContainer(target)

        if (myCloseButton != null) {
          val offset = JBUI.scale(20)
          val size = myCloseButton.preferredSize
          myCloseButton.setBounds(target.width - offset, offset - size.height, size.width, size.height)
        }

        if (myHelpButton != null) {
          val helpButton = myHelpButton!!
          val firstButton = myButtons[0]

          val iconPoint = SwingUtilities.convertPoint(myIconComponent, 0, 0, target)
          val buttonPoint = SwingUtilities.convertPoint(firstButton, 0, 0, target)

          val iconSize = myIconComponent.preferredSize
          val helpSize = helpButton.preferredSize
          val buttonSize = firstButton.preferredSize

          helpButton.setBounds(iconPoint.x + (iconSize.width - helpSize.width) / 2,
                               buttonPoint.y + (buttonSize.height - helpSize.height) / 2, helpSize.width, helpSize.height)
        }
      }
    }
  }

  override fun createCenterPanel(): JComponent {
    val dialogPanel = JPanel(BorderLayout(JBUI.scale(20), 0))

    val iconPanel = JPanel(BorderLayout())
    iconPanel.add(myIconComponent, BorderLayout.NORTH)
    dialogPanel.add(iconPanel, BorderLayout.WEST)

    val textPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
    dialogPanel.add(textPanel)

    if (myTitleComponent && !StringUtil.isEmpty(myTitle)) {
      val titleComponent = createTextComponent(myTitle)
      titleComponent.font = JBFont.create(titleComponent.font).biggerOn(3f).asBold()
      textPanel.add(titleComponent, BorderLayout.NORTH)
    }

    if (!StringUtil.isEmpty(myMessage)) {
      val messageComponent = createTextComponent(myMessage)
      val lines = myMessage!!.length / 100
      val scrollPane = Messages.wrapToScrollPaneIfNeeded(messageComponent, 100, 15, if (lines < 4) 4 else lines)
      if (scrollPane is JScrollPane) {
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
      }
      textPanel.add(object : Wrapper(scrollPane) {
        override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
          val maxWidth = JBUI.scale(450)
          super.setBounds(x, y, if (width > maxWidth) maxWidth else width, height)
        }
      })
    }

    textPanel.add(mySouthPanel, BorderLayout.SOUTH)

    createSouthPanel()

    if (myCheckBoxDoNotShowDialog == null || !myCheckBoxDoNotShowDialog.isVisible) {
      myButtonsPanel.border = JBUI.Borders.emptyTop(12)
    }
    else {
      val wrapper = Wrapper(myCheckBoxDoNotShowDialog)
      wrapper.border = JBUI.Borders.empty(8, 0)
      mySouthPanel.add(wrapper, BorderLayout.NORTH)
    }

    var gap = 12
    if (myButtons.size > 1) {
      val insets = myButtons[0].insets
      gap -= insets.left + insets.right
    }
    myButtonsPanel.layout = HorizontalLayout(JBUI.scale(gap))

    for (button in myButtons) {
      button.parent.remove(button)
      myButtonsPanel.add(button, HorizontalLayout.RIGHT)
    }

    mySouthPanel.add(myButtonsPanel)

    return dialogPanel
  }

  private fun createTextComponent(message: @Nls String?): JTextPane {
    val component = JTextPane()
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isOpaque = false
    component.isFocusable = false
    component.border = null
    component.editorKit = UIUtil.getHTMLEditorKit()
    component.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    component.text = message
    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }
    return component
  }

  override fun createContentPaneBorder(): Border = JBUI.Borders.empty(20)

  override fun createActions(): Array<Action> {
    val actions: MutableList<Action> = ArrayList()
    for (i in myOptions.indices) {
      val option = myOptions[i]
      val action: Action = object : AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
        override fun actionPerformed(e: ActionEvent) {
          close(i, true)
        }
      }
      if (i == myDefaultOptionIndex) {
        action.putValue(DEFAULT_ACTION, java.lang.Boolean.TRUE)
      }
      if (i == myFocusedOptionIndex) {
        action.putValue(FOCUSED_ACTION, java.lang.Boolean.TRUE)
      }
      UIUtil.assignMnemonic(option, action)
      actions.add(action)
    }

    if (helpId != null) {
      actions.add(helpAction)
    }
    return actions.toTypedArray()
  }

  override fun createJButtonForAction(action: Action?): JButton {
    val button = super.createJButtonForAction(action)
    val size = button.preferredSize
    val width100 = JBUI.scale(100)
    if (size.width < width100) {
      size.width = width100
    }
    else {
      val diffWidth = JBUI.scale(20) - UIUtil.getButtonTextHorizontalOffset(button, size, null)
      if (diffWidth > 0) {
        size.width += 2 * diffWidth
      }
    }
    val insets = button.insets
    size.width += insets.left + insets.right
    size.height += insets.top + insets.bottom
    button.preferredSize = size
    myButtons.add(button)
    return button
  }

  override fun createDoNotAskCheckbox(): JComponent? = null

  override fun getPreferredFocusedComponent(): JComponent? {
    return super.getPreferredFocusedComponent() ?: myCheckBoxDoNotShowDialog
  }

  override fun doCancelAction() = close(-1)

  override fun createHelpButton(insets: Insets): JButton {
    val helpButton = super.createHelpButton(insets)
    myHelpButton = helpButton
    return helpButton
  }

  override fun getHelpId(): String? = myHelpId
}