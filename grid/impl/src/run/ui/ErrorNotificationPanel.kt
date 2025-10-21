package com.intellij.database.run.ui

import com.intellij.CommonBundle
import com.intellij.database.DataGridBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.TextCopyProvider
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.font.TextAttribute
import javax.swing.*

private const val HORIZONTAL_LAYOUT_THRESHOLD = 2
private const val VERTICAL_MARGINS = 10
private const val HEIGHT_BETWEEN_TEXT_AND_BUTTONS = 6
private const val HORIZONTAL_MARGINS = 16

/**
 * Notification panel displayed at the bottom of the editor when execution fails.
 *
 * To provide custom fixes, implement the
 * [com.intellij.database.connection.throwable.info.RuntimeErrorActionProvider]
 * extension point and register it in your `plugin.xml` under the
 * `database.runtimeErrorFixProvider` key.
 */

class ErrorNotificationPanel private constructor(
  htmlErrorMessage: String?,
  items: List<PanelItem>,
  private val hideErrorAction: Runnable?,
  private val messageType: MessageType = MessageType.ERROR,
) : JPanel(BorderLayout()), UiDataProvider {
  private var copyProvider: CopyProvider? = null
  private val textPane: JTextArea?
  private val content: JPanel

  init {
    background = messageType.popupBackground
    val horizontalLayout = items.size <= HORIZONTAL_LAYOUT_THRESHOLD
    content = JPanel(BorderLayout())
    textPane = htmlErrorMessage?.let { createTextPanel(htmlErrorMessage, horizontalLayout) }
    initComponent(items, horizontalLayout)
  }

  @Deprecated("This method is deprecated and will be deleted soon." +
              " To provide custom fixes to notification panel, implement the" +
              " [com.intellij.database.connection.throwable.info.RuntimeErrorActionProvider]" +
              " extension point and declare it in your plugin.xml using the" +
              " 'database.runtimeErrorFixProvider' key")
  fun getContent(): JComponent? = content

  private fun initComponent(items: List<PanelItem>, horizontalLayout: Boolean) {
    val buttonsGravity = if (horizontalLayout) { BorderLayout.EAST } else { BorderLayout.NORTH }
    val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, HORIZONTAL_MARGINS, 0)).apply {
      items.forEach { add(it.buildComponent()) }
      isOpaque = false
      border = JBUI.Borders.empty()
    }

    content.add(buttonPanel, buttonsGravity)
    textPane?.let { content.add(it, BorderLayout.CENTER) }
    addSubscribers(textPane)

    content.isFocusable = true
    content.isFocusCycleRoot = true
    content.isOpaque = false
    content.isRequestFocusEnabled = true
    content.border = JBUI.Borders.empty(VERTICAL_MARGINS, 0)

    content.addMouseListener(RequestFocusMouseListener(this))
    add(content, BorderLayout.CENTER)

    textPane?.addMouseListener(RequestFocusMouseListener(this))
  }

  private fun createTextPanel(htmlText: String, horizontalLayout: Boolean) = JTextArea().apply {
    isOpaque = false
    isEditable = false
    isFocusable = true
    text = htmlText.trimIndent()
    font = UIManager.getFont("ToolTip.font")
    lineWrap = true
    border = if (horizontalLayout) {
      JBUI.Borders.empty(0, VERTICAL_MARGINS, 0, 0)
    } else {
      JBUI.Borders.empty(HEIGHT_BETWEEN_TEXT_AND_BUTTONS, HORIZONTAL_MARGINS, 0, HORIZONTAL_MARGINS)
    }
    wrapStyleWord = true
    foreground = messageType.titleForeground
    caret = object : javax.swing.text.DefaultCaret() {
      override fun paint(g: Graphics?) { /* hide cursor */ }
    }
  }

  override fun addMouseListener(listener: MouseListener) {
    content.addMouseListener(listener)
    textPane?.addMouseListener(listener)
  }

  private fun addSubscribers(textPane: JTextArea?) {
    object : DumbAwareAction(DataGridBundle.message("action.close.text")) {
      override fun actionPerformed(e: AnActionEvent) {
        hideErrorAction?.run()
      }
    }.registerCustomShortcutSet(CustomShortcutSet(KeyEvent.VK_ESCAPE), this)

    copyProvider = object : TextCopyProvider() {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      override fun getTextLinesToCopy(): MutableCollection<String?>? {
        textPane ?: return null
        textPane.selectedText?.let { return mutableSetOf(it) }
        return mutableSetOf(textPane.text)
      }
    }
  }

  override fun getMinimumSize(): Dimension = JBUI.emptySize()

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.COPY_PROVIDER] = copyProvider
  }

  class Builder internal constructor(
    private val message: @NlsContexts.NotificationContent String?,
    private val error: Throwable?,
  ) {
    private var errorMessage: String? = null
    private val items = mutableListOf<PanelItem>()
    private var type: MessageType = MessageType.ERROR
    private var hideErrorAction: Runnable? = null

    private var isChoppedMessage = false

    init {
      errorMessage = when {
        message != null -> getNormalized(message)
        error != null -> getNormalizedMessage(error)
        else -> null
      }
    }

    fun messageType(type: MessageType): Builder {
      this.type = type
      return this
    }

    fun addIconLink(tooltipText: @NlsContexts.Tooltip String?, realIcon: Icon, action: Runnable?): Builder {
      items.add(IconLink(realIcon, { action?.run() }, tooltipText))
      return this
    }

    fun addSpace(): Builder {
      items.add(EmptySpace())
      return this
    }

    fun addLink(link: @NlsActions.ActionText String, mnemonicCode: Int? = null, action: Runnable): Builder {
      items.add(TextLink(link, { action.run() }, mnemonicCode))
      return this
    }

    fun addDetailsButton(): Builder {
      val message: String? = when {
        error == null -> message
        error.stackTrace.size > 0 -> com.intellij.util.ExceptionUtil.getThrowableText(error, "com.intellij.")
        else -> error.message
      }

      return addLink(DataGridBundle.message("action.details.text")) {
        ErrorNotificationPopup(DataGridBundle.message("dialog.title.query.error"), null, message).show()
      }
    }

    fun addFullMessageButtonIfNeeded(): Builder {
      if (!isChoppedMessage) return this
      return addLink(DataGridBundle.message("action.full.message.text"), KeyEvent.VK_F, Runnable {
        ErrorNotificationPopup(DataGridBundle.message("action.full.message.text"), error, message).show()
      })
    }

    fun addCloseButton(action: Runnable?): Builder {
      hideErrorAction = action
      return addIconLink(DataGridBundle.message("tooltip.close.esc"), AllIcons.Actions.Close, action)
    }

    fun build(): ErrorNotificationPanel {
      val result = ErrorNotificationPanel(errorMessage, items, hideErrorAction, this.type)
      return result
    }

    @Suppress("HardCodedStringLiteral")
    private fun getNormalizedMessage(error: Throwable): @NlsContexts.NotificationContent String {
      var sourceMessage = StringUtil.notNullize(error.message, DataGridBundle.message(
        "notification.content.unknown.problem.occurred.see.details"))
      // In some cases a source message contains a stacktrace inside. Let's chop it
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
  }

  private interface PanelItem {
    fun buildComponent(): JComponent
  }

  private class TextLink(
    @Nls private val linkText: String,
    private val onClickAction: () -> Unit,
    private val mnemonicCode: Int? = null,
  ) : PanelItem {
    override fun buildComponent(): JComponent {
      return JButton().apply {
        foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        text = linkText
        font = UIManager.getFont("ToolTip.font")

        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        val fontMetrics = getFontMetrics(font)
        preferredSize = JBDimension(fontMetrics.stringWidth(linkText), fontMetrics.height)
        border = JBUI.Borders.empty()

        isFocusable = true
        if (mnemonicCode != null) {
          mnemonic = mnemonicCode
          addActionListener { onClickAction() }
          MnemonicHelper.registerMnemonicAction(this, mnemonicCode)
        }
        addMouseListener(MouseClickedListener(onClickAction, this))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      }
    }
  }

  private class IconLink(
    private val icon: Icon,
    private val onClickAction: () -> Unit,
    @Nls private val tooltipText: String? = null,
  ) : PanelItem {
    override fun buildComponent(): JComponent {
      return JLabel(icon).apply {
        toolTipText = tooltipText

        addMouseListener(MouseClickedListener(onClickAction, this))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      }
    }
  }

  private class EmptySpace() : PanelItem {
    override fun buildComponent(): JComponent {
      return Box.createRigidArea(JBDimension(1, 20)) as JComponent
    }
  }

  private class MouseClickedListener(
    private val onClickAction: () -> Unit,
    private val component: JComponent,
  ) : MouseListener {
    private val originalFont = component.font
    private val underlinedFont = originalFont.deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
    override fun mouseClicked(e: MouseEvent?) = onClickAction()
    override fun mousePressed(e: MouseEvent?) = Unit
    override fun mouseReleased(e: MouseEvent?) = Unit
    override fun mouseEntered(e: MouseEvent?) {
      component.font = underlinedFont
    }

    override fun mouseExited(e: MouseEvent?) {
      component.font = originalFont
    }
  }

  private class RequestFocusMouseListener(
    private val component: JComponent,
  ) : MouseListener {
    override fun mouseClicked(e: MouseEvent?) {
      component.requestFocusInWindow()
    }
    override fun mousePressed(e: MouseEvent?) = Unit
    override fun mouseReleased(e: MouseEvent?) = Unit
    override fun mouseEntered(e: MouseEvent?) = Unit
    override fun mouseExited(e: MouseEvent?) = Unit
  }

  companion object {
    @JvmStatic
    fun create(message: @NlsContexts.NotificationContent String?, error: Throwable?): Builder {
      return Builder(message, error)
    }
  }
}