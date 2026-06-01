package com.intellij.platform.lsp

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@TestApplication
internal class LspCompletionTest {
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
      completionProvider = CompletionOptions().apply {
        resolveProvider = true
        triggerCharacters = listOf("|")
      }
    },
  )

  @Nested
  inner class BasicCompletion {
    @Test
    fun `basic completion returns items from server`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("hello"),
          CompletionItem("world"),
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(setOf("hello", "world"), lookupElements!!.map { it.lookupString }.toSet())
    }

    @Test
    fun `completion item kinds are correctly mapped`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("myFunction").apply { kind = CompletionItemKind.Function },
          CompletionItem("MyClass").apply { kind = CompletionItemKind.Class },
          CompletionItem("myVariable").apply { kind = CompletionItemKind.Variable },
          CompletionItem("myKeyword").apply { kind = CompletionItemKind.Keyword },
          CompletionItem("myModule").apply { kind = CompletionItemKind.Module },
          CompletionItem("myProperty").apply { kind = CompletionItemKind.Property },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(6, lookupElements!!.size)
      val byName = lookupElements.associateBy { LookupElementPresentation.renderElement(it).itemText }
      assertEquals(AllIcons.Nodes.Function, byName["myFunction"]!!.let { LookupElementPresentation.renderElement(it) }.icon)
      assertEquals(AllIcons.Nodes.Class, byName["MyClass"]!!.let { LookupElementPresentation.renderElement(it) }.icon)
      assertEquals(AllIcons.Nodes.Variable, byName["myVariable"]!!.let { LookupElementPresentation.renderElement(it) }.icon)
      assertNull(byName["myKeyword"]!!.let { LookupElementPresentation.renderElement(it) }.icon)
      assertTrue(byName["myKeyword"]!!.let { LookupElementPresentation.renderElement(it) }.isItemTextBold)
      assertEquals(AllIcons.Nodes.Property, byName["myProperty"]!!.let { LookupElementPresentation.renderElement(it) }.icon)
    }

    @Test
    fun `completion items with textEdit are applied correctly`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("hello").apply {
            textEdit = Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 0)), "hello"))
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()
      assertNotNull(lookupElements)
      assertEquals(1, lookupElements!!.size)
      assertEquals("hello", lookupElements[0].lookupString)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("hello<caret>")
      }
    }

    @Test
    fun `completion items without textEdit use insertText`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("displayLabel").apply {
            insertText = "actualInsertedText"
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()
      assertNotNull(lookupElements)
      assertEquals(1, lookupElements!!.size)
      assertEquals("actualInsertedText", lookupElements[0].lookupString)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("actualInsertedText<caret>")
      }
    }

    @Test
    fun `completion items fall back to label when no insertText`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("fallbackLabel"),
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()
      assertNotNull(lookupElements)
      assertEquals(1, lookupElements!!.size)
      assertEquals("fallbackLabel", lookupElements[0].lookupString)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("fallbackLabel<caret>")
      }
    }
  }

  @Nested
  inner class TriggerCharacters {
    @Test
    fun `completion triggered by triggerCharacters from server capabilities`(): Unit = timeoutRunBlocking {
      val autoPopupTester = CompletionAutoPopupTester(codeInsightFixture)

      val virtualFile = codeInsightFixture.configureByText("test.txt", "foo<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, {
        it.textDocument.uri == serverSession.fileUri(virtualFile)
      }) {
        Either.forLeft(listOf(CompletionItem("bar")))
      }

      autoPopupTester.runWithAutoPopupEnabled {
        autoPopupTester.typeWithPauses("|")
      }
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class CompletionItemResolve {
    @Test
    fun `completion item resolve fetches additional details`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("myItem").apply {
            kind = CompletionItemKind.Function
            data = "resolve-data"
          },
        ))
      }

      serverSession.expectRequest(serverSession.COMPLETION_ITEM_RESOLVE, { it.label == "myItem" }) {
        CompletionItem("myItem_resolved").apply {
          kind = CompletionItemKind.Function
          detail = "fun myItem(): String"
          documentation = Either.forLeft("Returns a string value")
        }
      }

      val lookupElements = codeInsightFixture.completeBasic()
      assertNotNull(lookupElements)
      val element = lookupElements!!.single()

      val presentation = LookupElementPresentation()
      @Suppress("UNCHECKED_CAST")
      (element.expensiveRenderer as LookupElementRenderer<LookupElement>).renderElement(element, presentation)
      serverSession.awaitExpected()

      assertEquals("myItem", presentation.itemText)
      assertEquals("fun myItem(): String", presentation.typeText)
      assertEquals(AllIcons.Nodes.Function, presentation.icon)
    }

    @Test
    fun `resolved item updates presentation`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("resolveMe").apply {
            data = "needs-resolve"
          },
        ))
      }

      serverSession.expectRequest(serverSession.COMPLETION_ITEM_RESOLVE, { it.label == "resolveMe" }) {
        CompletionItem("resolveMe_resolved").apply {
          detail = "Resolved detail"
          labelDetails = CompletionItemLabelDetails().apply {
            detail = "(param: Int)"
            description = "String"
          }
        }
      }

      val lookupElements = codeInsightFixture.completeBasic()
      assertNotNull(lookupElements)
      val element = lookupElements!!.single()

      val presentation = LookupElementPresentation()
      @Suppress("UNCHECKED_CAST")
      (element.expensiveRenderer as LookupElementRenderer<LookupElement>).renderElement(element, presentation)
      serverSession.awaitExpected()

      assertEquals("resolveMe", presentation.itemText)
      assertEquals("(param: Int)", presentation.tailText)
      assertEquals("String", presentation.typeText)
    }
  }

  @Nested
  inner class CompletionPrefix {
    @Test
    fun `completion prefix calculated from textEdit range`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hel<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("hello").apply {
            textEdit = Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 3)), "hello"))
          },
          CompletionItem("help").apply {
            textEdit = Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 3)), "help"))
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(setOf("hello", "help"), lookupElements!!.map { it.lookupString }.toSet())
    }
  }

  @Nested
  inner class ItemPresentation {
    @Test
    fun `completion item strikeout for deprecated tag`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("deprecatedItem").apply {
            kind = CompletionItemKind.Function
            tags = listOf(CompletionItemTag.Deprecated)
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val presentation = LookupElementPresentation.renderElement(lookupElements!!.single())
      assertTrue(presentation.isStrikeout)
    }

    @Test
    fun `completion item strikeout for deprecated property`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      @Suppress("DEPRECATION")
      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("oldItem").apply {
            kind = CompletionItemKind.Method
            deprecated = true
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val presentation = LookupElementPresentation.renderElement(lookupElements!!.single())
      assertTrue(presentation.isStrikeout)
    }

    @Test
    fun `completion item tailText from labelDetails detail`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("myFunction").apply {
            kind = CompletionItemKind.Function
            labelDetails = CompletionItemLabelDetails().apply {
              detail = "(x: Int, y: String)"
            }
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val presentation = LookupElementPresentation.renderElement(lookupElements!!.single())
      assertEquals("(x: Int, y: String)", presentation.tailText)
    }

    @Test
    fun `completion item typeText from labelDetails description`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("myFunction").apply {
            kind = CompletionItemKind.Function
            labelDetails = CompletionItemLabelDetails().apply {
              description = "String"
            }
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val presentation = LookupElementPresentation.renderElement(lookupElements!!.single())
      assertEquals("String", presentation.typeText)
    }

    @Test
    fun `completion item typeText falls back to detail`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("myFunction").apply {
            kind = CompletionItemKind.Function
            detail = "fun myFunction(): String"
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val presentation = LookupElementPresentation.renderElement(lookupElements!!.single())
      assertEquals("fun myFunction(): String", presentation.typeText)
    }
  }

  @Nested
  inner class InsertTextFormatTests {
    @Test
    fun `plaintext format completion items inserted as-is`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("plainItem").apply {
            insertText = "plainText"
            insertTextFormat = InsertTextFormat.PlainText
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()
      assertNotNull(lookupElements)
      assertEquals(1, lookupElements!!.size)
      assertEquals("plainText", lookupElements[0].lookupString)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("plainText<caret>")
      }
    }
  }

  @Nested
  inner class InsertTextModeTests {
    @Test
    fun `adjustIndentation insert text mode adjusts whitespace`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "  <caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("indented").apply {
            insertText = "line1\n  line2\n  line3"
            insertTextMode = InsertTextMode.AdjustIndentation
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val element = lookupElements!!.single()
      assertEquals("line1\n  line2\n  line3", element.lookupString)
      assertEquals("indented", LookupElementPresentation.renderElement(element).itemText)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("  line1\n  line2\n  line3<caret>")
      }
    }

    @Test
    fun `asIs insert text mode preserves whitespace`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "  <caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("preserved").apply {
            insertText = "line1\nline2\nline3"
            insertTextMode = InsertTextMode.AsIs
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      val element = lookupElements!!.single()
      assertEquals("line1\nline2\nline3", element.lookupString)
      assertEquals("preserved", LookupElementPresentation.renderElement(element).itemText)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("  line1\nline2\nline3<caret>")
      }
    }
  }

  @Nested
  inner class AdditionalTextEdits {
    @Test
    fun `completion item with additionalTextEdits applies all edits`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("importedSymbol").apply {
            insertText = "importedSymbol"
            additionalTextEdits = listOf(
              TextEdit(Range(Position(0, 0), Position(0, 0)), "import { importedSymbol } from 'module'\n"),
            )
          },
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()
      assertNotNull(lookupElements)
      assertEquals(1, lookupElements!!.size)
      assertEquals("importedSymbol", lookupElements[0].lookupString)

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("import { importedSymbol } from 'module'\nimportedSymbol<caret>")
      }
    }
  }

  @Disabled("not implemented in production yet")
  @Nested
  inner class CommitCharacters {
    @Test
    fun `commitCharacters from completion item trigger completion`() = timeoutRunBlocking {
      codeInsightFixture.type('|')
    }
  }
}
