package com.intellij.platform.lsp

import com.intellij.formatting.service.FormattingService
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.lang.ImportOptimizer
import com.intellij.lang.LanguageImportStatements
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LspOptimizeImportsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      val codeActionOptions = CodeActionOptions().apply {
        resolveProvider = true
        codeActionKinds = listOf(CodeActionKind.SourceFixAll, CodeActionKind.SourceOrganizeImports)
      }
      codeActionProvider = Either.forRight(codeActionOptions)
    }
  )

  @Test
  fun `action availability`() = timeoutRunBlocking {
    val otherOptimizer = object : ImportOptimizer {
      override fun supports(file: PsiFile): Boolean = file.name == "otherOptimizer.txt"
      override fun processFile(file: PsiFile): Runnable = Runnable {}
    }
    LanguageImportStatements.INSTANCE
      .addExplicitExtension(PlainTextLanguage.INSTANCE, otherOptimizer, codeInsightFixture.testRootDisposable)

    val lspFormattingService = FormattingService.EP_NAME.findByIdOrFromInstance("LspFormattingService", { null })!!

    val psiFile1 = codeInsightFixture.configureByText("otherOptimizer.txt", "")
    awaitFileOpenedByLspServer(project, psiFile1.virtualFile)
    assertFalse(lspFormattingService.canFormat(psiFile1, Feature.OPTIMIZE_IMPORTS))

    val psiFile2 = codeInsightFixture.configureByText("lspShouldWork.txt", "")
    awaitFileOpenedByLspServer(project, psiFile2.virtualFile)
    assertTrue(lspFormattingService.canFormat(psiFile2, Feature.OPTIMIZE_IMPORTS))
  }

  @Test
  fun `optimize imports with legacy changes format`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "Alpha Beta Gamma").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(
      serverSession.CODE_ACTION,
      { it.textDocument.uri == fileUri && it.context.only?.contains(CodeActionKind.SourceOrganizeImports) == true },
    ) {
      listOf<Either<Command, CodeAction>>(
        Either.forRight(CodeAction().apply {
          title = "Organize Imports"
          kind = CodeActionKind.SourceOrganizeImports
          data = "resolve-token-1"
        })
      )
    }

    serverSession.expectRequest(serverSession.CODE_ACTION_RESOLVE, { it.data?.toString() == "\"resolve-token-1\"" }) {
      CodeAction().apply {
        title = "Organize Imports"
        kind = CodeActionKind.SourceOrganizeImports
        edit = WorkspaceEdit(mapOf(
          fileUri to listOf(
            TextEdit(Range(Position(0, 6), Position(0, 6)), "\n"),
            TextEdit(Range(Position(0, 11), Position(0, 11)), "\n")
          )
        ))
      }
    }

    codeInsightFixture.performEditorAction("OptimizeImports")
    serverSession.awaitExpected()
    codeInsightFixture.checkResult("Alpha \nBeta \nGamma")
  }

  @Test
  fun `optimize imports with multiple code actions should be ignored`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test.txt", "A B C D").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
      listOf<Either<Command, CodeAction>>(
        Either.forRight(CodeAction().apply { title = "First"; kind = CodeActionKind.SourceOrganizeImports; data = "t1" }),
        Either.forRight(CodeAction().apply { title = "Second"; kind = CodeActionKind.SourceOrganizeImports; data = "t2" })
      )
    }

    codeInsightFixture.performEditorAction("OptimizeImports")
    serverSession.awaitExpected()
    codeInsightFixture.checkResult("A B C D")
  }

  @Test
  fun `no code actions from the LSP server`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test3.txt", "No imports to organize").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == fileUri }) {
      emptyList()
    }

    codeInsightFixture.performEditorAction("OptimizeImports")
    serverSession.awaitExpected()
    codeInsightFixture.checkResult("No imports to organize")
  }

  @Test
  fun `optimize imports - both modern and legacy edits present are ignored`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("both.txt", "Alpha Beta").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(
      serverSession.CODE_ACTION,
      { it.textDocument.uri == fileUri && it.context.only?.contains(CodeActionKind.SourceOrganizeImports) == true },
    ) {
      listOf<Either<Command, CodeAction>>(
        Either.forRight(CodeAction().apply {
          title = "Organize Imports"
          kind = CodeActionKind.SourceOrganizeImports
          data = "resolve-both"
        })
      )
    }

    serverSession.expectRequest(serverSession.CODE_ACTION_RESOLVE, { it.data?.toString() == "\"resolve-both\"" }) {
      CodeAction().apply {
        title = "Organize Imports"
        kind = CodeActionKind.SourceOrganizeImports
        edit = WorkspaceEdit().apply {
          changes = mapOf(
            fileUri to listOf(
              TextEdit(Range(Position(0, 5), Position(0, 5)), "\n")
            )
          )
          documentChanges = listOf(
            Either.forLeft(
              TextDocumentEdit(
                VersionedTextDocumentIdentifier(fileUri, null),
                listOf(TextEdit(Range(Position(0, 0), Position(0, 1)), "Z"))
              )
            )
          )
        }
      }
    }

    codeInsightFixture.performEditorAction("OptimizeImports")
    serverSession.awaitExpected()
    codeInsightFixture.checkResult("Alpha Beta")
  }
}
