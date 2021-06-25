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
import com.intellij.ui.*
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.MacMessages
import com.intellij.ui.mac.TouchbarDataKeys
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.JBWordWrapHtmlEditorKit
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.math.min

/**
 * @author Alexander Lobas
 */

@Service
class AlertMessagesManager : MacMessages() {
  companion object {
    @JvmStatic
    fun isEnabled(): Boolean {
      val app = ApplicationManager.getApplication()
      return app != null && !app.isUnitTestMode && !app.isHeadlessEnvironment && Registry.`is`("ide.message.dialogs.as.swing.alert", true)
    }

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
    val dialog = AlertDialog(null, window, message, title, arrayOf(yesText, cancelText, noText), 0, -1, getIcon(icon), doNotAskOption,
                             helpId)
    dialog.show()
    val exitCode = when (dialog.exitCode) {
      0 -> Messages.YES
      2 -> Messages.NO
      else -> Messages.CANCEL
    }
    doNotAskOption?.setToBeShown(dialog.toBeShown(), exitCode)
    return exitCode
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
    return showMessageDialog(null, window, message, title, arrayOf(yesText, noText), 0, -1, icon, doNotAskDialogOption,
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
}

private const val PARENT_WIDTH_KEY = "parent.width"

private class AlertDialog(project: Project?,
                          parentComponent: Component?,
                          @NlsContexts.DialogMessage val myMessage: String?,
                          @NlsContexts.DialogTitle val myTitle: String?,
                          val myOptions: Array<String>,
                          val myDefaultOptionIndex: Int,
                          val myFocusedOptionIndex: Int,
                          icon: Icon,
                          doNotAskOption: DoNotAskOption?,
                          val myHelpId: String?) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE, false) {

  private val myIsTitleComponent = SystemInfoRt.isMac || !Registry.`is`("ide.message.dialogs.as.swing.alert.show.title.bar", false)

  private val myRootLayout = RootLayout()
  private val myIconComponent = JLabel(icon)
  private var myTitleComponent: JComponent? = null
  private var myMessageComponent: JComponent? = null
  private val mySouthPanel = JPanel(BorderLayout())
  private val myButtonsPanel = JPanel()
  private val myCloseButton: JComponent?
  private val myButtons = ArrayList<JButton>()
  private var myHelpButton: JButton? = null

  init {
    title = myTitle
    setDoNotAskOption(doNotAskOption)

    if (myIsTitleComponent && !SystemInfoRt.isMac) {
      myCloseButton = InplaceButton(IconButton(null, AllIcons.Ide.Notification.Close, AllIcons.Ide.Notification.CloseHover, null)) {
        doCancelAction()
      }
    }
    else {
      myCloseButton = null
    }

    if (SystemInfoRt.isMac) {
      setInitialLocationCallback {
        val rootPane: JRootPane? = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
        if (rootPane == null) {
          return@setInitialLocationCallback null
        }
        val location = rootPane.locationOnScreen
        Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
      }
    }

    init()

    if (myHelpButton != null) {
      val helpButton = myHelpButton!!
      helpButton.parent.remove(helpButton)
      myRootLayout.addAdditionalComponent(helpButton)
    }

    if (myCloseButton != null) {
      myRootLayout.addAdditionalComponent(myCloseButton)
    }

    if (myIsTitleComponent) {
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      rootPane.border = PopupBorder.Factory.create(true, true)

      object : MouseDragHelper<JComponent>(myDisposable, contentPane as JComponent) {
        var myLocation: Point? = null

        override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
          val target = dragComponent.findComponentAt(dragComponentPoint)
          return target == null || target == dragComponent || target == myTitleComponent || target is JPanel
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

  override fun getInitialSize(): Dimension {
    val buttonsWidth = myButtonsPanel.preferredSize.width

    if (buttonsWidth > JBUI.scale(348)) {
      configureMessageWidth(min(buttonsWidth, JBUI.scale(450)))
      return preferredSize
    }

    val width = JBUI.scale(if (buttonsWidth <= JBUI.scale(278) && StringUtil.length(myMessage) <= 130) 370 else 440)

    configureMessageWidth(width - myRootLayout.getWidthWithoutMessageComponent())

    return Dimension(width, preferredSize.height)
  }

  private fun configureMessageWidth(width: Int) {
    val scrollPane = ComponentUtil.getScrollPane(myMessageComponent)
    if (scrollPane == null) {
      myMessageComponent!!.putClientProperty(PARENT_WIDTH_KEY, width)
    }
    else {
      scrollPane.preferredSize = Dimension(width, scrollPane.preferredSize.height)
    }
  }

  override fun createContentPaneBorder(): Border {
    val insets = JButton().insets
    return JBUI.Borders.empty(if (myIsTitleComponent) 20 else 14, 20, 18 - insets.bottom, 20 - insets.right)
  }

  override fun createRootLayout(): LayoutManager = myRootLayout

  private inner class RootLayout : BorderLayout() {
    private var myParent: Container? = null

    fun getWidthWithoutMessageComponent(): Int {
      val insets = myParent!!.insets
      val layout = myIconComponent.parent.parent.layout as BorderLayout
      return insets.left + myIconComponent.preferredSize.width + layout.hgap + insets.right
    }

    fun addAdditionalComponent(component: Component) {
      myParent!!.add(component, 0)
    }

    override fun addLayoutComponent(name: String?, comp: Component) {
      if (myParent == null) {
        myParent = comp.parent
      }
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

  override fun createCenterPanel(): JComponent {
    val dialogPanel = JPanel(BorderLayout(JBUI.scale(20), 0))

    val iconPanel = JPanel(BorderLayout())
    iconPanel.add(myIconComponent, BorderLayout.NORTH)
    dialogPanel.add(iconPanel, BorderLayout.WEST)

    val textPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
    dialogPanel.add(textPanel)

    if (myIsTitleComponent && !StringUtil.isEmpty(myTitle)) {
      val titleComponent = createTextComponent(JTextPane(), UIUtil.removeMnemonic(myTitle!!))
      titleComponent.font = JBFont.h3().asBold()
      myTitleComponent = titleComponent
      textPanel.add(titleComponent, BorderLayout.NORTH)
    }

    if (!StringUtil.isEmpty(myMessage)) {
      val messageComponent = createTextComponent(object : JTextPane() {
        override fun getPreferredSize(): Dimension {
          val parentWidth = getClientProperty(PARENT_WIDTH_KEY)
          if (parentWidth is Int) {
            val view = getUI().getRootView(this)
            view.setSize(parentWidth.toFloat(), Int.MAX_VALUE.toFloat())
            return Dimension(parentWidth, view.getPreferredSpan(View.Y_AXIS).toInt())
          }
          return super.getPreferredSize()
        }
      }, myMessage!!.replace("(\r\n|\n)".toRegex(), "<br/>"))

      messageComponent.font = JBFont.medium()
      myMessageComponent = messageComponent

      val lines = myMessage.length / 100
      val scrollPane = Messages.wrapToScrollPaneIfNeeded(messageComponent, 100, 15, if (lines < 4) 4 else lines)
      if (scrollPane is JScrollPane) {
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
      }
      textPanel.add(object : Wrapper(scrollPane) {
        override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
          super.setBounds(x, y, min(width, JBUI.scale(450)), height)
        }
      })
    }

    textPanel.add(mySouthPanel, BorderLayout.SOUTH)

    createSouthPanel()

    val buttonInsets = if (myButtons.size > 0) myButtons[0].insets else Insets(0, 0, 0, 0)

    if (myCheckBoxDoNotShowDialog == null || !myCheckBoxDoNotShowDialog.isVisible) {
      // vertical gap 22 between text message and visual part of buttons
      myButtonsPanel.border = JBUI.Borders.emptyTop(14 - buttonInsets.top) // +8 from textPanel layout vGap
    }
    else {
      myCheckBoxDoNotShowDialog.font = JBFont.medium()
      val wrapper = Wrapper(myCheckBoxDoNotShowDialog)
      // vertical gap 12 between text message and check box
      wrapper.border = JBUI.Borders.emptyTop(4) // +8 from textPanel layout vGap
      // vertical gap 22 between check box and visual part of buttons
      (mySouthPanel.layout as BorderLayout).vgap = JBUI.scale(22 - buttonInsets.top)
      mySouthPanel.add(wrapper, BorderLayout.NORTH)
    }

    myButtonsPanel.layout = HorizontalLayout(JBUI.scale(12 - buttonInsets.left - buttonInsets.right))

    for (button in myButtons) {
      button.parent.remove(button)
      myButtonsPanel.add(button, HorizontalLayout.RIGHT)
    }

    if (SystemInfoRt.isMac) {
      val buttonMap = buttonMap
      buttonMap.clear()

      for ((index, button) in myButtons.withIndex()) {
        buttonMap[button.action] = button
        button.putClientProperty(TouchbarDataKeys.DIALOG_BUTTON_DESCRIPTOR_KEY, null)
        val descriptor = TouchbarDataKeys.putDialogButtonDescriptor(button, index + 1, true)
        if (button.action.getValue(DEFAULT_ACTION) != null) {
          descriptor.isDefault = true
        }
      }
    }

    mySouthPanel.add(myButtonsPanel)

    return dialogPanel
  }

  private fun createTextComponent(component: JTextPane, message: @Nls String?): JTextPane {
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isOpaque = false
    component.isFocusable = false
    component.border = null

    val kit = JBWordWrapHtmlEditorKit()
    kit.styleSheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "}")
    component.editorKit = kit
    component.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    if (BasicHTML.isHTMLString(message)) {
      component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                  StringUtil.unescapeXmlEntities(StringUtil.stripHtml(message!!, " ")))
    }

    component.text = message

    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }

    return component
  }

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

  override fun createJButtonForAction(action: Action): JButton {
    val button = super.createJButtonForAction(action)
    val size = button.preferredSize
    val insets = button.insets

    val width100 = JBUI.scale(100) + insets.left + insets.right
    if (size.width < width100) {
      size.width = width100
    }
    else {
      val diffWidth = JBUI.scale(20) - UIUtil.getButtonTextHorizontalOffset(button, size, null)
      if (diffWidth > 0) {
        size.width += 2 * diffWidth
      }
    }

    val height28 = JBUI.scale(28) + insets.top + insets.bottom
    if (size.height < height28) {
      size.height = height28
    }

    button.preferredSize = size

    if (SystemInfoRt.isMac) {
      myButtons.add(0, button)
    }
    else {
      myButtons.add(button)
    }

    return button
  }

  override fun createDoNotAskCheckbox(): JComponent? = null

  override fun getPreferredFocusedComponent(): JComponent? {
    val focusedComponent = super.getPreferredFocusedComponent()
    if (SystemInfoRt.isMac && focusedComponent == null) {
      if (myCheckBoxDoNotShowDialog != null && myCheckBoxDoNotShowDialog.isVisible) {
        return myCheckBoxDoNotShowDialog
      }
      if (myButtons.isNotEmpty()) {
        return myButtons[0]
      }
    }
    return focusedComponent
  }

  override fun doCancelAction() = close(-1)

  override fun createHelpButton(insets: Insets): JButton {
    val helpButton = super.createHelpButton(insets)
    myHelpButton = helpButton
    return helpButton
  }

  override fun getHelpId(): String? = myHelpId

  public override fun toBeShown(): Boolean = super.toBeShown()
}