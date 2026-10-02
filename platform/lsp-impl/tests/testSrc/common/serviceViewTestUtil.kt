package com.intellij.platform.lsp.common

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.serviceView.LspClientConsole
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewContributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stops the fake servers and waits until their processes end.
 * The client leaves the Services view before its process ends. A live process reader thread at the end of a test is a thread leak.
 */
internal suspend fun stopFakeServers(project: Project) {
  val clientManager = LspClientManager.getInstance(project)
  val servers = clientManager.getClients(FakeLspIntegrationProvider::class.java).map { (it.descriptor as FakeLspClientDescriptor).server }
  clientManager.stopClients(FakeLspIntegrationProvider::class.java)
  while (LspServiceViewContributor().getServices(project).isNotEmpty()) {
    delay(50.milliseconds)
  }
  for (server in servers) {
    runInterruptible(Dispatchers.IO) { server.waitFor() }
  }
}

/** Waits until the text of [console] contains all [expected] parts, and returns that text. */
internal suspend fun awaitConsoleText(console: LspClientConsole, expected: List<String>): String {
  var consoleText = ""
  while (expected.any { it !in consoleText }) {
    delay(50.milliseconds)
    consoleText = withContext(Dispatchers.EDT) { console.getConsoleTextForTests() }
  }
  return consoleText
}

internal fun assertContentType(consoleView: ConsoleViewImpl, marker: String, expected: ConsoleViewContentType) {
  val editor = requireNotNull(consoleView.editor) { "The console editor should be initialized" }
  val offset = editor.document.text.indexOf(marker)
  assertTrue(offset >= 0, "Marker '$marker' should be present in the console text")
  // console tokens are highlighted in the document markup model, see ConsoleTokenUtil
  val markupModel = DocumentMarkupModel.forDocument(editor.document, consoleView.project, true)
  val coveringKeys = markupModel.allHighlighters
    .filter { it.isValid && it.startOffset <= offset && offset < it.endOffset }
    .mapNotNull { it.textAttributesKey }
  val expectedKey = requireNotNull(expected.attributesKey) { "The expected content type should have an attributes key" }
  assertTrue(expectedKey in coveringKeys,
             "Marker '$marker' should be highlighted as ${expectedKey.externalName}, actual keys: " +
             coveringKeys.map { it.externalName })
}

internal suspend fun awaitErrorOutput(lspClient: LspClient, marker: String) {
  while ((lspClient as LspClientImpl).errorOutput?.contains(marker) != true) {
    delay(50.milliseconds)
  }
}

/** Runs [block] with the notifications that [project] shows meanwhile. Then expires them. */
internal suspend fun collectNotifications(project: Project, block: suspend CoroutineScope.(Collection<Notification>) -> Unit) {
  val notifications = ConcurrentLinkedQueue<Notification>()
  val connection = project.messageBus.connect()
  try {
    connection.subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(notification)
      }
    })
    coroutineScope { block(notifications) }
  }
  finally {
    connection.disconnect()
    notifications.forEach { it.expire() }
  }
}

internal suspend fun awaitNotification(notifications: Collection<Notification>, marker: String): Notification {
  while (true) {
    notifications.singleOrNull { marker in it.content }?.let { return it }
    delay(50.milliseconds)
  }
}
