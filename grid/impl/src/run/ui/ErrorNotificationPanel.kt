package com.intellij.database.run.ui

import com.intellij.CommonBundle
import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.GridUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.IdeTooltipManager.Companion.getInstance
import com.intellij.ide.IdeTooltipManager.Companion.initPane
import com.intellij.ide.TextCopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.UiDataProvider.Companion.wrapComponent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HintHint
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.Consumer
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class ErrorNotificationPanel private constructor(
  htmlErrorMessage: @NlsContexts.NotificationContent String,
  private val myActions: MutableMap<String?, Runnable?>,
  private val myType: MessageType
) : JPanel(BorderLayout()) {
  private val myMessagePane: JEditorPane
  private val myCopyProvider: CopyProvider

  init {
    setBorder(JBUI.Borders.empty(0, 4))

    myMessagePane = initPane(htmlErrorMessage, HintHint()
      .setAwtTooltip(false)
      .setTextFg(getForeground())
      .setTextBg(getBackground())
      .setBorderColor(getBackground())
      .setBorderInsets(JBInsets.emptyInsets()), null)
    myMessagePane.setBorder(null)
    myMessagePane.addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        performAction(e.description)
      }
    })
    myCopyProvider = object : TextCopyProvider() {
      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
      }

      override fun getTextLinesToCopy(): MutableCollection<String?>? {
        val text = myMessagePane.getSelectedText()
        return if (StringUtil.isEmpty(text)) null else mutableSetOf<String?>(text)
      }
    }
    add(wrapComponent(myMessagePane, UiDataProvider { sink: DataSink? ->
      sink!!.set<CopyProvider>(PlatformDataKeys.COPY_PROVIDER, this.myCopyProvider)
    }), BorderLayout.CENTER)

    object : DumbAwareAction(DataGridBundle.message("action.close.text")) {
      override fun actionPerformed(e: AnActionEvent) {
        performAction(DataGridBundle.message("action.close.text"))
      }
    }.registerCustomShortcutSet(CustomShortcutSet(KeyEvent.VK_ESCAPE), this)
  }

  private fun performAction(actionName: String?) {
    myActions[actionName]?.run()
  }

  override fun getBackground(): Color {
    return type.popupBackground
  }

  override fun getForeground(): Color {
    return type.titleForeground
  }

  private val type: MessageType
    get() = ObjectUtils.chooseNotNull<MessageType>(myType, MessageType.ERROR)

  override fun getMinimumSize(): Dimension {
    return JBUI.emptySize()
  }

  val content: JComponent
    get() = myMessagePane

  class Builder internal constructor(
    private val myMessage: @NlsContexts.NotificationContent String?,
    private val myError: Throwable?,
    private val myBaseComponent: JComponent
  ) {
    private val myLongMessage: Boolean

    private val myActions: MutableMap<String?, Runnable?> = LinkedHashMap<String?, Runnable?>()
    private val myShowHideHandlers: MutableList<Consumer<Disposable?>> = ArrayList<Consumer<Disposable?>>()
    private val myHtmlBuilder = StringBuilder()

    private var isChoppedMessage = false

    private var myType: MessageType = MessageType.ERROR

    init {
      var errorMessage = if (myMessage == null) if (myError == null) null else getNormalizedMessage(myError)
      else getNormalized(
        myMessage)
      val font = getInstance().getTextFont(true)
      val fm = myBaseComponent.getFontMetrics(font)
      myLongMessage = SwingUtilities.computeStringWidth(fm, errorMessage) > myBaseComponent.getWidth() * 3 / 4
      if (errorMessage != null) {
        errorMessage = StringUtil.escapeXmlEntities(errorMessage).replace("\n", "<br>")
      }

      myHtmlBuilder.append("<html><head><style type=\"text/css\">a:link {text-decoration:none;}</style></head><body>")
      myHtmlBuilder.append("<font face=\"verdana\"><table width=\"100%\"><tr valign=\"top\"><td>")
      myHtmlBuilder.append(errorMessage)
      myHtmlBuilder.append("</td>")
    }

    fun messageType(type: MessageType): Builder {
      myType = type
      return this
    }

    fun addIconLink(command: String?, tooltipText: @NlsContexts.Tooltip String?, realIcon: Icon, action: Runnable?): Builder {
      val iconPath = GridUtil.getIconPath(realIcon)

      startActionColumn()
      myHtmlBuilder.append("<a href=\"")
        .append(command).append("\"><icon alt=\"").append(tooltipText).append("\"")
        .append("\" src=\"")
      myHtmlBuilder.append(iconPath).append("\"></a>")
      endActionColumn()

      if (action != null) {
        myActions.put(command, action)
      }

      return this
    }

    fun addSpace(): Builder {
      startActionColumn()
      endActionColumn()
      return this
    }

    fun addLink(command: @NonNls String, linkHtml: @NlsActions.ActionText String, action: Runnable): Builder {
      startActionColumn()
      val mnemonicIndex = UIUtil.getDisplayMnemonicIndex(command)
      val fixedCommand: @NlsSafe String = if (mnemonicIndex < 0) command else command.substring(0, mnemonicIndex) + command.substring(mnemonicIndex + 1)
      ContainerUtil.addIfNotNull<Consumer<Disposable?>?>(myShowHideHandlers,
                                                         createMnemonicActionIfNeeded(fixedCommand, mnemonicIndex, action, myBaseComponent))
      myHtmlBuilder.append("<a style=\"text-decoration:none;\" href=\"").append(fixedCommand).append("\">").append(linkHtml).append("</a>")
      endActionColumn()
      myActions.put(fixedCommand, action)
      return this
    }

    fun addDetailsButton(): Builder {
      val message: String = (if (myError == null) myMessage
      else if (myError.stackTrace.size > 0) com.intellij.util.ExceptionUtil.getThrowableText(myError, "com.intellij.")
      else myError.message)!!
      if (StringUtil.contains(myHtmlBuilder, message)) return this
      return addLink("details", DataGridBundle.message("action.details.text"), Runnable {
        Messages.showIdeaMessageDialog(null, message,
                                       DataGridBundle.message("dialog.title.query.error"),
                                       arrayOf<String>(CommonBundle.getOkButtonText()), 0, Messages.getErrorIcon(), null)
      })
    }

    fun addFullMessageButtonIfNeeded(): Builder {
      if (!isChoppedMessage) return this
      return addLink("details", DataGridBundle.message("action.full.message.text"), Runnable {
        Messages.showIdeaMessageDialog(null, myMessage,
                                       DataGridBundle.message("dialog.title.query.error"),
                                       arrayOf<String>(CommonBundle.getOkButtonText()), 0, Messages.getErrorIcon(), null
        )
      })
    }

    fun addCloseButton(action: Runnable?): Builder {
      return addIconLink(DataGridBundle.message("action.close.text"), DataGridBundle.message("tooltip.close.esc"),
                         AllIcons.Actions.Close, action)
    }

    fun build(): ErrorNotificationPanel {
      myHtmlBuilder.append("</tr></table></font></body>")
      val result = ErrorNotificationPanel(myHtmlBuilder.toString(), myActions, myType) //NON-NLS
      registerShowHideHandlers(result)
      return result
    }

    private fun startActionColumn() {
      myHtmlBuilder.append("<td width=\"1%\" align=\"right\" valign=\"")
        .append(if (myLongMessage) "top" else "middle")
        .append("\" nowrap><div style='margin:0px 2px 0px 2px'>")
    }

    private fun endActionColumn() {
      myHtmlBuilder.append("</div></td>")
    }

    private fun getNormalizedMessage(error: Throwable): @NlsContexts.NotificationContent String {
      var sourceMessage = StringUtil.notNullize(error.message,
                                                DataGridBundle.message(
                                                  "notification.content.unknown.problem.occurred.see.details")) + "kgmsdkgmksdfgnmksndfgknskdfgnksndgkndfkgnsdkfgnskdngkndsfgksnkgnfksdngksdnfgksndkgnsdkfgnksdnfgkndkfgnskngfksnkgfnksdnfgksdnfgknsdkgnslgnskldfnglksnfgksnfgksnkfgnksdfngksdnfgksdngksndfgknsdkf"
      // In some cases source message contains stacktrace inside. Let's chop it
      val divPos = sourceMessage.indexOf("\n\tat ")
      if (divPos != -1) {
        sourceMessage = sourceMessage.substring(0, divPos)
        isChoppedMessage = true
      }
      return getNormalized(sourceMessage)
    }

    private fun getNormalized(sourceMessage: @NlsContexts.NotificationContent String): @NlsContexts.NotificationContent String {
      val lineLimit = StringUtil.lineColToOffset(sourceMessage, 5, 0)
      val charLimit = 1024
      val limit = if (lineLimit == -1 || lineLimit > charLimit) charLimit else lineLimit
      if (sourceMessage.length > limit) isChoppedMessage = true
      return StringUtil.trimLog(sourceMessage, limit + 1)
    }

    private fun registerShowHideHandlers(component: JComponent) {
      if (myShowHideHandlers.isEmpty()) return

      component.addHierarchyListener(object : HierarchyListener {
        private var myShownDisposable: Disposable? = null
        override fun hierarchyChanged(e: HierarchyEvent) {
          val c = e.component
          if (c == null || (e.getChangeFlags() and HierarchyEvent.SHOWING_CHANGED.toLong()) <= 0) return

          if (c.isShowing()) {
            myShownDisposable = Disposer.newDisposable()
            for (handler in myShowHideHandlers) {
              handler.consume(myShownDisposable)
            }
            return
          }

          if (myShownDisposable != null) Disposer.dispose(myShownDisposable!!)
          myShownDisposable = null
        }
      })
    }

    companion object {
      private fun createMnemonicActionIfNeeded(
        command: @NlsActions.ActionText String,
        mnemonicIndex: Int,
        runnable: Runnable,
        component: JComponent?
      ): Consumer<Disposable?>? {
        if (mnemonicIndex < 0) return null
        return { parentDisposable: Disposable? ->
          val a: DumbAwareAction = object : DumbAwareAction(command) {
            override fun actionPerformed(e: AnActionEvent) {
              runnable.run()
            }
          }
          val modifiers = if (SystemInfo.isMac && !`is`(
              "ide.mac.alt.mnemonic.without.ctrl")
          ) InputEvent.ALT_MASK or InputEvent.CTRL_MASK
          else InputEvent.ALT_MASK
          val keyStroke = KeyStroke.getKeyStroke(command.get(mnemonicIndex).uppercaseChar().code, modifiers)
          a.registerCustomShortcutSet(CustomShortcutSet(keyStroke), component, parentDisposable)
        } as? Consumer<Disposable?>
      }
    }
  }

  companion object {
    @JvmStatic
    fun create(message: @NlsContexts.NotificationContent String?, error: Throwable?, baseComponent: JComponent): Builder {
      return Builder(message, error, baseComponent)
    }
  }
}
