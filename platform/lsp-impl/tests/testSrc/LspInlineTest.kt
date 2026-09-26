// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.refactoring.actions.ExtractMethodAction
import com.intellij.refactoring.actions.InlineAction
import com.intellij.refactoring.actions.IntroduceFieldAction
import com.intellij.refactoring.actions.IntroduceVariableAction
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionDisabled
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeActionRegistrationOptions
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class LspInlineTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private class InlineAndExtractCustomization : LspCustomization() {
    override val codeActionsCustomizer = object : LspCodeActionsSupport() {
      override fun shouldRunRefactoring(psiFile: PsiFile): Boolean = true
    }
  }

  private class DroppingCustomization : LspCustomization() {
    override val codeActionsCustomizer = object : LspCodeActionsSupport() {
      override fun shouldRunRefactoring(psiFile: PsiFile): Boolean = true
      override fun createIntentionAction(lspClient: LspClient, codeAction: CodeAction): LspIntentionAction? =
        if (codeAction.title == "Skip") null else super.createIntentionAction(lspClient, codeAction)
    }
  }

  @Nested
  inner class WithExtractCapability {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf(CodeActionKind.RefactorInline, CodeActionKind.RefactorExtract)
        })
      },
    )

    // the inline action runs outside any command, so applying the returned edit must start its own
    @Test
    fun `inline applies the single returned code action`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }

    // a server may answer with a bare Command instead of a CodeAction; the kind filter must keep it
    @Test
    fun `inline runs the single returned bare command on the server`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forLeft(Command("Inline variable", "inlineVariable", listOf(fileUri))))
      }
      serverSession.expectRequest(serverSession.EXECUTE_COMMAND, { it.command == "inlineVariable" }) { }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
    }

    // the platform hands Inline the resolved target, which can live in another file; the request must still go to the editor's document
    @Test
    fun `inline uses the editor's file when the target element lives in another file`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("other.txt", "foo")
      val otherElement = readAction { otherFile.findElementAt(0)!! }
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        val action = InlineAction()
        val dataContext = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(codeInsightFixture.editor.contentComponent))
          .add(CommonDataKeys.PSI_ELEMENT, otherElement)
          .build()
        val event = TestActionEvent.createTestEvent(action, dataContext)
        ActionUtil.updateAction(action, event)
        assertTrue(event.presentation.isEnabled, "Inline must be available when the target's file is not open by the server")
        ActionUtil.performAction(action, event)
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }
  }

  @Nested
  inner class WithoutExtractCapability {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf(CodeActionKind.RefactorInline)
        })
      },
    )

    // the inline action resolves its handler from InlineActionHandler, so an inline-only server needs no Extract provider
    @Test
    fun `inline works when the server supports no extract actions`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }

    // the Extract providers gate on their own kinds, so an inline-only server leaves them unavailable
    @Test
    fun `extract is unavailable when the server supports no extract actions`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      configureServerSession(project, virtualFile)

      withContext(Dispatchers.EDT) {
        val presentation = codeInsightFixture.testAction(IntroduceVariableAction())
        assertFalse(presentation.isEnabled)
      }
      codeInsightFixture.checkResult("foo bar")
    }
  }

  @Nested
  inner class WithVariableExtractCapabilityOnly {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf("refactor.extract.variable")
        })
      },
    )

    // each Extract action gates on its own kind, so one supported subtype must not enable the others
    @Test
    fun `extract method is unavailable when the server supports extract variable only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      configureServerSession(project, virtualFile)

      withContext(Dispatchers.EDT) {
        val presentation = codeInsightFixture.testAction(ExtractMethodAction())
        assertFalse(presentation.isEnabled)
      }
      codeInsightFixture.checkResult("foo bar")
    }

    @Test
    fun `introduce variable works when the server supports extract variable only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Introduce variable"
          kind = "refactor.extract.variable"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "extracted"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(IntroduceVariableAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("extracted bar")
    }
  }

  @Nested
  inner class WithConstantExtractCapabilityOnly {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf("refactor.extract.constant")
        })
      },
    )

    // a server can serve its only scoped-value extraction under `refactor.extract.constant`, so the fallback kind must answer
    @Test
    fun `introduce variable falls back to extract constant when the primary kind yields nothing`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.variable") },
      ) {
        emptyList()
      }
      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.constant") },
      ) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Extract to constant in enclosing scope"
          kind = "refactor.extract.constant"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "extracted"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(IntroduceVariableAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("extracted bar")
    }

    // a constant extraction in a class scope yields a field on such a server, so Introduce Field takes the same fallback
    @Test
    fun `introduce field falls back to extract constant when the primary kind yields nothing`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.field") },
      ) {
        emptyList()
      }
      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.constant") },
      ) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Extract to readonly field"
          kind = "refactor.extract.constant"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "extracted"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(IntroduceFieldAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("extracted bar")
    }
  }

  @Nested
  inner class WithVariableAndConstantExtractCapabilities {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf("refactor.extract.variable", "refactor.extract.constant")
        })
      },
    )

    // the actions come from the first kind that yields any, so the fallback kind must not mix its actions in
    @Test
    fun `introduce variable takes the primary kind and does not request the fallback`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val variableRequested = serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.context.only == listOf("refactor.extract.variable") },
      ) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Introduce variable"
          kind = "refactor.extract.variable"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "extracted"),
          )))
        }))
      }
      val constantRequested = serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.context.only == listOf("refactor.extract.constant") },
      ) {
        emptyList()
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(IntroduceVariableAction())
      }

      variableRequested.await()
      codeInsightFixture.checkResult("extracted bar")
      assertFalse(constantRequested.isCompleted, "The fallback kind must not be requested when the primary kind yields actions")
      constantRequested.cancel()
    }

    // a disabled action is not a result, so it must not stop the fallback to the next kind
    @Test
    fun `introduce variable falls back to extract constant when the primary kind yields only disabled actions`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.variable") },
      ) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Introduce variable"
          kind = "refactor.extract.variable"
          disabled = CodeActionDisabled("The selection is not an expression")
        }))
      }
      serverSession.expectRequest(
        serverSession.CODE_ACTION,
        { it.textDocument.uri == fileUri && it.context.only == listOf("refactor.extract.constant") },
      ) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Introduce constant"
          kind = "refactor.extract.constant"
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "extracted"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(IntroduceVariableAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("extracted bar")
    }
  }

  @Nested
  inner class WithDroppingCustomization {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = DroppingCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf(CodeActionKind.RefactorInline)
        })
      },
    )

    // the refactoring shortcuts respect the plugin's code action filter like the intention popup does
    @Test
    fun `inline skips the code actions the customizer drops`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(
          Either.forRight(CodeAction().apply {
            title = "Skip"
            kind = CodeActionKind.RefactorInline
            edit = WorkspaceEdit(mapOf(fileUri to listOf(
              TextEdit(Range(Position(0, 0), Position(0, 3)), "skipped"),
            )))
          }),
          Either.forRight(CodeAction().apply {
            title = "Inline variable"
            kind = CodeActionKind.RefactorInline
            edit = WorkspaceEdit(mapOf(fileUri to listOf(
              TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
            )))
          }),
        )
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }
  }

  @Nested
  inner class WithQuickFixCapabilityOnly {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf(CodeActionKind.QuickFix)
        })
      },
    )

    // a server can add code action kinds with a dynamic registration; the static list must not veto them
    @Test
    fun `inline works for a dynamic registration next to a static code action capability`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("inline-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.RefactorInline)
        })))
      }

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }
  }

  @Nested
  inner class WithSimilarlyNamedExtractCapability {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
      configureServerCapabilities = {
        codeActionProvider = Either.forRight(CodeActionOptions().apply {
          codeActionKinds = listOf("refactor.extract.variable2")
        })
      },
    )

    // `refactor.extract.variable2` is outside the `refactor.extract.variable` subtree, so it must not enable Introduce Variable
    @Test
    fun `introduce variable is unavailable for a similarly prefixed capability kind`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      awaitFileOpenedByLspServer(project, virtualFile)
      configureServerSession(project, virtualFile)

      withContext(Dispatchers.EDT) {
        val presentation = codeInsightFixture.testAction(IntroduceVariableAction())
        assertFalse(presentation.isEnabled)
      }
      codeInsightFixture.checkResult("foo bar")
    }
  }

  @Nested
  inner class WithoutCodeActionCapability {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = InlineAndExtractCustomization(),
    )

    // a server can register its code action support dynamically instead of the static capabilities
    @Test
    fun `inline works for a dynamically registered code action capability`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("inline-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
          codeActionKinds = listOf(CodeActionKind.RefactorInline)
        })))
      }

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }

    // a server may split its code action kinds across registrations, so the first match must not veto the rest
    @Test
    fun `inline works when an earlier dynamic registration advertises other kinds only`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo bar").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(
          Registration("quick-fixes", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
            codeActionKinds = listOf(CodeActionKind.QuickFix)
          }),
          Registration("inline-actions", "textDocument/codeAction", CodeActionRegistrationOptions().apply {
            codeActionKinds = listOf(CodeActionKind.RefactorInline)
          }),
        ))
      }

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
        listOf(Either.forRight(CodeAction().apply {
          title = "Inline variable"
          kind = CodeActionKind.RefactorInline
          edit = WorkspaceEdit(mapOf(fileUri to listOf(
            TextEdit(Range(Position(0, 0), Position(0, 3)), "inlined"),
          )))
        }))
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.testAction(InlineAction())
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("inlined bar")
    }
  }
}
