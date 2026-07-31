package com.intellij.platform.lsp.impl.serviceView

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel

private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
private const val MAX_TRAFFIC_PAYLOAD_LENGTH = 10_000
private const val MAX_STORED_PAYLOAD_LENGTH = 100_000

/**
 * A console shown in the Services tool window for a single [com.intellij.platform.lsp.api.LspClient].
 * Messages may arrive from any thread before the UI component is created; [ConsoleView] buffers them.
 */
internal class LspClientConsole(project: Project) : Disposable {
  private val console: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).apply { setViewer(true) }.console
  private val ansiDecoder = AnsiEscapeDecoder()
  private var panel: JComponent? = null

  init {
    Disposer.register(this, console)
  }

  fun printLogMessage(type: MessageType, message: String): Unit =
    print(type.levelTag(), message, type.contentType())

  fun printShowMessage(type: MessageType, message: String): Unit =
    print(type.levelTag(), "window/showMessage: $message", type.contentType())

  fun printTrace(message: String): Unit =
    print("TRACE", message, ConsoleViewContentType.LOG_VERBOSE_OUTPUT)

  fun printLifecycle(message: @NlsSafe String, error: Boolean = false): Unit =
    print(null, message, if (error) ConsoleViewContentType.LOG_ERROR_OUTPUT else ConsoleViewContentType.SYSTEM_OUTPUT)

  @Synchronized
  fun printTraffic(outbound: Boolean, message: Message, json: String) {
    val header = when (message) {
      is RequestMessage -> "request '${message.method}' (id=${message.id})"
      is ResponseMessage -> "response (id=${message.id})"
      is NotificationMessage -> "notification '${message.method}'"
      else -> "message"
    }
    val payloadPreview = if (json.length > MAX_TRAFFIC_PAYLOAD_LENGTH) {
      "${json.take(MAX_TRAFFIC_PAYLOAD_LENGTH)}… (${json.length - MAX_TRAFFIC_PAYLOAD_LENGTH} more characters)"
    }
    else json
    val contentType = if (outbound) ConsoleViewContentType.LOG_DEBUG_OUTPUT else ConsoleViewContentType.LOG_VERBOSE_OUTPUT
    val tag = if (outbound) "OUT" else "IN"
    val arrow = if (outbound) "→" else "←"

    console.print("${LocalTime.now().format(TIMESTAMP_FORMAT)} ${tag.padEnd(5)} ", contentType)
    console.printHyperlink(header, LspTrafficPayloadHyperlinkInfo(
      header = "$arrow $header",
      json = json.take(MAX_STORED_PAYLOAD_LENGTH),
      truncated = json.length > MAX_STORED_PAYLOAD_LENGTH,
    ))
    console.print(": ${payloadPreview.trimEnd()}\n", contentType)
  }

  @Synchronized
  private fun print(tag: @NlsSafe String?, message: @NlsSafe String, contentType: ConsoleViewContentType, decodeAnsi: Boolean = true) {
    val cleanedMessage = if (decodeAnsi) {
      StringBuilder().also { builder ->
        ansiDecoder.escapeText(message, ProcessOutputTypes.STDOUT) { text, _ -> builder.append(text) }
      }
    }
    else message

    val line = buildString {
      append(LocalTime.now().format(TIMESTAMP_FORMAT)).append(' ')
      if (tag != null) append(tag.padEnd(5)).append(' ')
      append(cleanedMessage.trimEnd())
      append('\n')
    }
    console.print(line, contentType)
  }

  @RequiresEdt
  fun getComponent(): JComponent {
    panel?.let { return it }

    val newPanel = JPanel(BorderLayout())
    newPanel.add(console.component, BorderLayout.CENTER)

    val toolbar = ActionManager.getInstance()
      .createActionToolbar("LspServiceViewConsole", DefaultActionGroup(*console.createConsoleActions()), false)
    toolbar.targetComponent = console.component
    newPanel.add(toolbar.component, BorderLayout.EAST)

    panel = newPanel
    return newPanel
  }

  @TestOnly
  @RequiresEdt
  fun getConsoleTextForTests(): String = getConsoleViewForTests().text

  @TestOnly
  @RequiresEdt
  fun getConsoleViewForTests(): ConsoleViewImpl {
    val consoleViewImpl = console as ConsoleViewImpl
    consoleViewImpl.component // make sure the console editor is initialized
    consoleViewImpl.flushDeferredText()
    return consoleViewImpl
  }

  override fun dispose() {
    panel = null
  }
}

private fun MessageType.levelTag(): @NlsSafe String = when (this) {
  MessageType.Error -> "ERROR"
  MessageType.Warning -> "WARN"
  MessageType.Info -> "INFO"
  MessageType.Log -> "LOG"
}

private fun MessageType.contentType(): ConsoleViewContentType = when (this) {
  MessageType.Error -> ConsoleViewContentType.LOG_ERROR_OUTPUT
  MessageType.Warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT
  MessageType.Info -> ConsoleViewContentType.LOG_INFO_OUTPUT
  MessageType.Log -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
}
