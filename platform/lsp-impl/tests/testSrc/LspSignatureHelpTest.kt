package com.intellij.platform.lsp

import com.intellij.codeInsight.hint.ParameterInfoControllerBase
import com.intellij.codeInsight.hint.ParameterInfoListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSignatureHelpCustomizer
import com.intellij.platform.lsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.lsp.api.customization.LspSignatureHelpSupport
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.AutoPopupParameterInfoTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.EditorHintFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SignatureHelpRegistrationOptions
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Tuple
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specification/#textDocument_signatureHelp)
 * behind Parameter Info.
 *
 * The platform opens Parameter Info on `(` and `,` in any file, so the tests of the trigger characters of the server use other characters.
 */
@TestApplication
internal class LspSignatureHelpTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private const val CARET = "<caret>"
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val editorHints by testFixture { _ ->
    val disposable = Disposer.newDisposable("editorHints")
    initialized(EditorHintFixture(disposable)) { Disposer.dispose(disposable) }
  }

  private val hintChanges by extensionPointFixture(ParameterInfoListener.EP_NAME) { HintChanges() }

  @Nested
  inner class StaticRegistration {
    private val fakeLspIntegration by signatureHelpIntegrationFixture()

    @Test
    fun `the hint shows the signature of the server with its active parameter highlighted`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature1.txt", "function('a',$CARET 1)",
                                   SignatureHelp(listOf(signature("param1: String", "param2: Int")), 0, 1))

      assertEquals("<html>function(param1: String, <b>param2: Int</b>)</html>", hint)
    }

    @Test
    fun `the active parameter of the signature is highlighted`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature2.txt", "function(1, 2, 3$CARET)",
                                   SignatureHelp(listOf(signature("a", "b", "c", activeParameter = 2)), 0, null))

      assertEquals("<html>function(a, b, <b>c</b>)</html>", hint)
    }

    @Test
    fun `the active parameter of the signature takes precedence over the one of the answer`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature3.txt", "function(1, 2$CARET)",
                                   SignatureHelp(listOf(signature("a", "b", activeParameter = 1)), null, 0))

      assertEquals("<html>function(a, <b>b</b>)</html>", hint)
    }

    @Test
    fun `the first signature is shown when the answer has no active signature`(): Unit = timeoutRunBlocking {
      val signatures = listOf(signature("a", "b", activeParameter = 1), signature("a", "b", "c", activeParameter = 2))
      val hint = parameterInfoHint("signature4.txt", "function(1, 2$CARET)", SignatureHelp(signatures, null, null))

      assertEquals("<html>function(a, <b>b</b>)</html>", hint)
    }

    @Test
    fun `the first signature is shown when the active signature is out of range`(): Unit = timeoutRunBlocking {
      val signatures = listOf(signature("a", "b", activeParameter = 1), signature("a", "b", "c", activeParameter = 2))
      val hint = parameterInfoHint("signature5.txt", "function(1, 2$CARET)", SignatureHelp(signatures, 5, null))

      assertEquals("<html>function(a, <b>b</b>)</html>", hint)
    }

    @Test
    fun `the first parameter is highlighted when the active parameter of the answer is out of range`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature6.txt", "function(1, 2$CARET)", SignatureHelp(listOf(signature("a", "b")), null, 2))

      assertEquals("<html>function(<b>a</b>, b)</html>", hint)
    }

    @Test
    fun `no parameter is highlighted when the active parameter of the signature is out of range`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature7.txt", "function(1, 2$CARET)",
                                   SignatureHelp(listOf(signature("a", "b", activeParameter = 2)), null, 1))

      assertEquals("<html>function(a, b)</html>", hint)
    }

    @Test
    fun `no parameter is highlighted when neither the signature nor the answer has an active parameter`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature8.txt", "function(1, c = 2$CARET)", SignatureHelp(listOf(signature("a", "b")), null, null))

      assertEquals("<html>function(a, b)</html>", hint)
    }

    @Test
    fun `the active parameter is ignored when the signature has no parameters`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature9.txt", "function($CARET)", SignatureHelp(listOf(signature(activeParameter = 0)), null, 0))

      assertEquals("<html>function()</html>", hint)
    }

    @Test
    fun `a parameter label given as offsets is highlighted at those offsets`(): Unit = timeoutRunBlocking {
      val signature = SignatureInformation("function(a, b)").apply {
        parameters = listOf(9 to 10, 12 to 13).map { (start, end) ->
          ParameterInformation().apply { label = Either.forRight(Tuple.two(start, end)) }
        }
      }
      val hint = parameterInfoHint("signature10.txt", "function(1, 2$CARET)", SignatureHelp(listOf(signature), null, 1))

      assertEquals("<html>function(a, <b>b</b>)</html>", hint)
    }

    @Test
    fun `a parameter label that repeats an earlier label is highlighted after the earlier parameter`(): Unit = timeoutRunBlocking {
      val hint = parameterInfoHint("signature11.txt", "function(1, 2$CARET)", SignatureHelp(listOf(signature("x", "x")), null, 1))

      assertEquals("<html>function(x, <b>x</b>)</html>", hint)
    }

    @Test
    fun `Parameter Info asks the server once for the signatures and the active parameter`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature18.txt", "function(1, 2$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 13), SignatureHelp(listOf(signature("a", "b")), null, 1))

      changingHint { showParameterInfo() }

      assertEquals(1, serverSession.receivedRequests(serverSession.SIGNATURE_HELP).size)
      assertEquals("<html>function(a, <b>b</b>)</html>", hintText())
    }

    @Test
    fun `Parameter Info again at the same caret with no edit shows the kept answer`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature19.txt", "function(1, 2$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 13), SignatureHelp(listOf(signature("a", "b")), null, 1))
      changingHint { showParameterInfo() }

      changingHint { showParameterInfo() }

      assertEquals(1, serverSession.receivedRequests(serverSession.SIGNATURE_HELP).size)
      assertEquals("<html>function(a, <b>b</b>)</html>", hintText())
    }

    @Test
    fun `no hint when the server has no signatures`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature12.txt", "function(1$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 10), SignatureHelp(emptyList(), null, null))

      showParameterInfo()
      awaitParameterInfoTasks()

      serverSession.awaitExpected()
      assertNull(hintText())
    }

    @Test
    fun `no hint when the server answers null`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature13.txt", "function(1$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 10), null)

      showParameterInfo()
      awaitParameterInfoTasks()

      serverSession.awaitExpected()
      assertNull(hintText())
    }

    @Test
    fun `moving the caret to another argument asks again and highlights the new active parameter`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature14.txt", "function(1$CARET, 2)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 10), SignatureHelp(listOf(signature("a", "b")), null, 0))
      changingHint { showParameterInfo() }
      assertEquals("<html>function(<b>a</b>, b)</html>", hintText())

      serverSession.expectSignatureHelp(virtualFile, Position(0, 13), SignatureHelp(listOf(signature("a", "b")), null, 1))
      changingHint { moveCaret(13) }

      serverSession.awaitExpected()
      assertEquals("<html>function(a, <b>b</b>)</html>", hintText())
    }

    @Test
    fun `an edit asks again at the same caret position`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature15.txt", "function(1, 2$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 13), SignatureHelp(listOf(signature("a", "b")), null, 0))
      changingHint { showParameterInfo() }
      assertEquals("<html>function(<b>a</b>, b)</html>", hintText())

      serverSession.expectSignatureHelp(virtualFile, Position(0, 13), SignatureHelp(listOf(signature("a", "b")), null, 1))
      changingHint {
        writeCommandAction(project, "Add a line after the caret") {
          codeInsightFixture.editor.document.insertString(codeInsightFixture.editor.document.textLength, "\n")
        }
      }

      serverSession.awaitExpected()
      assertEquals("<html>function(a, <b>b</b>)</html>", hintText())
    }

    @Test
    fun `the hint hides when the server has no signatures at the new caret position`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature16.txt", "function(1$CARET) after").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 10), SignatureHelp(listOf(signature("a")), null, 0))
      changingHint { showParameterInfo() }
      assertEquals("<html>function(<b>a</b>)</html>", hintText())

      serverSession.expectSignatureHelp(virtualFile, Position(0, 17), SignatureHelp(emptyList(), null, null))
      changingHint { moveCaret(17) }

      serverSession.awaitExpected()
      assertNull(hintText())
    }

    @Test
    fun `typing a trigger character of the server shows the hint`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("signature17.txt", "generic$CARET").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, 8), SignatureHelp(listOf(signature("T")), null, 0))

      changingHint { type('<') }

      serverSession.awaitExpected()
      assertEquals("<html>function(<b>T</b>)</html>", hintText())
    }

    /**
     * Opens a file with the single-line [text], runs Parameter Info at its caret, and returns the text of the hint.
     * The server answers [signatureHelp] at the caret.
     */
    private suspend fun parameterInfoHint(fileName: String, text: String, signatureHelp: SignatureHelp): String? {
      val virtualFile = codeInsightFixture.configureByText(fileName, text).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectSignatureHelp(virtualFile, Position(0, text.indexOf(CARET)), signatureHelp)

      changingHint { showParameterInfo() }

      serverSession.awaitExpected()
      return hintText()
    }
  }

  @Nested
  inner class IgnoredTriggerCharacter {
    private val fakeLspIntegration by signatureHelpIntegrationFixture(object : LspSignatureHelpSupport() {
      override fun isTriggerCharacterRespected(charTyped: Char): Boolean = charTyped != '<'
    })

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `typing a trigger character that the customizer ignores shows no hint`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("ignored1.txt", "generic$CARET").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      type('<')
      awaitParameterInfoTasks()

      assertEquals(emptyList<Any>(), serverSession.receivedRequests(serverSession.SIGNATURE_HELP))
      assertNull(hintText())
    }
  }

  @Nested
  inner class SignatureHelpDisabled {
    private val fakeLspIntegration by signatureHelpIntegrationFixture(LspSignatureHelpDisabled)

    @Suppress("AssertBetweenInconvertibleTypes") // Qodana bug
    @Test
    fun `no request and no hint when the customizer disables the feature`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("disabled1.txt", "function(1$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      showParameterInfo()
      awaitParameterInfoTasks()

      assertEquals(emptyList<Any>(), serverSession.receivedRequests(serverSession.SIGNATURE_HELP))
      assertNull(hintText())
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `Parameter Info works with a dynamic registration only`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("dynamic1.txt", "function(1$CARET)").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("signature-help", "textDocument/signatureHelp", SignatureHelpRegistrationOptions())))
      }
      serverSession.expectSignatureHelp(virtualFile, Position(0, 10), SignatureHelp(listOf(signature("a")), null, 0))

      changingHint { showParameterInfo() }

      serverSession.awaitExpected()
      assertEquals("<html>function(<b>a</b>)</html>", hintText())
    }

    @Test
    fun `trigger characters come from every matching dynamic registration`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("dynamic2.txt", "generic$CARET").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(
          Registration("signature-help-1", "textDocument/signatureHelp", SignatureHelpRegistrationOptions().apply {
            triggerCharacters = listOf("<")
          }),
          Registration("signature-help-2", "textDocument/signatureHelp", SignatureHelpRegistrationOptions().apply {
            triggerCharacters = listOf("[")
          }),
        ))
      }
      serverSession.expectSignatureHelp(virtualFile, Position(0, 8), SignatureHelp(listOf(signature("index")), null, 0))

      changingHint { type('[') }

      serverSession.awaitExpected()
      assertEquals("<html>function(<b>index</b>)</html>", hintText())
    }
  }

  private fun signatureHelpIntegrationFixture(customizer: LspSignatureHelpCustomizer = LspSignatureHelpSupport()) =
    projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val signatureHelpCustomizer: LspSignatureHelpCustomizer = customizer
      },
      configureServerCapabilities = {
        signatureHelpProvider = SignatureHelpOptions(listOf("<"))
      },
    )

  private fun signature(vararg parameters: String, activeParameter: Int? = null): SignatureInformation =
    SignatureInformation("function(${parameters.joinToString()})").apply {
      this.parameters = parameters.map { ParameterInformation(it) }
      this.activeParameter = activeParameter
    }

  private fun FakeLspServerSession.expectSignatureHelp(virtualFile: VirtualFile, position: Position, signatureHelp: SignatureHelp?) {
    val fileUri = fileUri(virtualFile)
    expectRequest(SIGNATURE_HELP, { it.textDocument.uri == fileUri && it.position == position }) {
      signatureHelp ?: nullResponse()
    }
  }

  private suspend fun showParameterInfo() {
    withContext(Dispatchers.EDT) {
      codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
    }
  }

  private suspend fun type(char: Char) {
    withContext(Dispatchers.EDT) {
      codeInsightFixture.type(char)
    }
  }

  private suspend fun moveCaret(offset: Int) {
    withContext(Dispatchers.EDT) {
      codeInsightFixture.editor.caretModel.moveToOffset(offset)
    }
  }

  /** Runs [action] and waits until the hint updates or hides after it. The hint updates after it gets the answer of the server. */
  private suspend fun changingHint(action: suspend () -> Unit) {
    val changesBefore = hintChanges.count.value
    action()
    hintChanges.count.first { it > changesBefore }
  }

  /**
   * Waits for the auto-popup delay, the update delay of the hint, and the non-blocking read actions of Parameter Info,
   * as the tests of Parameter Info in other languages do. For the tests in which no hint shows, so [changingHint] has nothing to wait for.
   */
  private suspend fun awaitParameterInfoTasks() {
    withContext(Dispatchers.EDT) {
      repeat(5) {
        AutoPopupParameterInfoTestUtil.waitForAutoPopup(project)
        AutoPopupParameterInfoTestUtil.waitForParameterInfoUpdate(codeInsightFixture.editor)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }
  }

  /** The text of the Parameter Info hint, with the highlighted parameter in `<b>`, or `null` when no hint shows. */
  private suspend fun hintText(): String? = withContext(Dispatchers.EDT) {
    editorHints.currentHintText
  }

  private class HintChanges : ParameterInfoListener {
    val count = MutableStateFlow(0)

    override fun hintUpdated(result: ParameterInfoControllerBase.Model) {
      count.update { it + 1 }
    }

    override fun hintHidden(project: Project) {
      count.update { it + 1 }
    }
  }
}
