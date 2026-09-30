package com.intellij.platform.lsp

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.lsp.common.FakeLspIntegrationProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.completion.LspCompletionObject
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ApplyKind
import org.eclipse.lsp4j.CompletionApplyKind
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemDefaults
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InsertReplaceRange
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
import java.util.concurrent.CompletableFuture


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
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      completionProvider = CompletionOptions().apply {
        resolveProvider = true
        triggerCharacters = listOf("|")
      }
    },
  )

  /**
   * Waits until the lookup resolves [element] in the background, then renders [element] with its expensive renderer.
   * The render uses the resolved item and does not send a second `completionItem/resolve` request.
   *
   * Call this function only when the test expects a `completionItem/resolve` request that returns a non-null item.
   * Otherwise, the item never becomes resolved, and the function fails on timeout.
   */
  private suspend fun awaitResolveAndRender(element: LookupElement): LookupElementPresentation {
    val initialCompletionItem = (element as LookupElementDecorator<*>).delegate.`object`
    val completionObject = element.`object` as LspCompletionObject
    waitUntil("the lookup must resolve the completion item") { completionObject.completionItem !== initialCompletionItem }

    val presentation = LookupElementPresentation()
    @Suppress("UNCHECKED_CAST")
    (element.expensiveRenderer as LookupElementRenderer<LookupElement>).renderElement(element, presentation)
    return presentation
  }

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

      serverSession.awaitExpected()
      val presentation = awaitResolveAndRender(element)

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

      serverSession.awaitExpected()
      val presentation = awaitResolveAndRender(element)

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

  @Nested
  inner class ItemDefaults {
    @Test
    fun `default editRange is applied with textEditText or label`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hel<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forRight(CompletionList(false, listOf(
          CompletionItem("hello").apply { insertText = "ignoredInsertText" },
          CompletionItem("help").apply { textEditText = "helper" },
          CompletionItem("helium").apply {
            textEdit = Either.forLeft(TextEdit(Range(Position(0, 0), Position(0, 3)), "heliumOwnEdit"))
          },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply {
            editRange = Either.forLeft(Range(Position(0, 0), Position(0, 3)))
          }
        })
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(setOf("hello", "helper", "heliumOwnEdit"), lookupElements!!.map { it.lookupString }.toSet())
    }

    @Test
    fun `default insert-replace editRange is applied`(): Unit = timeoutRunBlocking {
      val insertRange = Range(Position(0, 0), Position(0, 3))
      val replaceRange = Range(Position(0, 0), Position(0, 5))
      val items = completeAndGetItems(
        "hel<caret>lo",
        CompletionList(false, listOf(CompletionItem("hello"))).apply {
          itemDefaults = CompletionItemDefaults().apply {
            editRange = Either.forRight(InsertReplaceRange().apply {
              insert = insertRange
              replace = replaceRange
            })
          }
        }
      )

      val textEdit = items["hello"]!!.textEdit!!.right!!
      assertEquals("hello", textEdit.newText)
      assertEquals(insertRange, textEdit.insert)
      assertEquals(replaceRange, textEdit.replace)
    }

    @Test
    fun `default Snippet insertTextFormat is applied on insertion`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forRight(CompletionList(false, listOf(
          CompletionItem("foo").apply { insertText = $$"foo($0)" },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply { insertTextFormat = InsertTextFormat.Snippet }
        })
      }

      assertNotNull(codeInsightFixture.completeBasic())
      serverSession.awaitExpected()

      withContext(Dispatchers.EDT) {
        codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        codeInsightFixture.checkResult("foo(<caret>)")
      }
    }

    @Test
    fun `default insertTextFormat and insertTextMode are applied only to items without own values`(): Unit = timeoutRunBlocking {
      val items = completeAndGetItems(
        "<caret>",
        CompletionList(false, listOf(
          CompletionItem("withoutOwnValues"),
          CompletionItem("withOwnValues").apply {
            insertTextFormat = InsertTextFormat.PlainText
            insertTextMode = InsertTextMode.AsIs
          },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply {
            insertTextFormat = InsertTextFormat.Snippet
            insertTextMode = InsertTextMode.AdjustIndentation
          }
        }
      )

      assertEquals(InsertTextFormat.Snippet, items["withoutOwnValues"]!!.insertTextFormat)
      assertEquals(InsertTextMode.AdjustIndentation, items["withoutOwnValues"]!!.insertTextMode)
      assertEquals(InsertTextFormat.PlainText, items["withOwnValues"]!!.insertTextFormat)
      assertEquals(InsertTextMode.AsIs, items["withOwnValues"]!!.insertTextMode)
    }

    @Test
    fun `commitCharacters and data are replaced if applyKind is not specified`(): Unit = timeoutRunBlocking {
      checkReplaceApplyKind(applyKind = null)
    }

    @Test
    fun `commitCharacters and data are replaced if applyKind is Replace`(): Unit = timeoutRunBlocking {
      checkReplaceApplyKind(CompletionApplyKind().apply {
        commitCharacters = ApplyKind.Replace
        data = ApplyKind.Replace
      })
    }

    private suspend fun CoroutineScope.checkReplaceApplyKind(applyKind: CompletionApplyKind?) {
      val items = completeAndGetItems(
        "<caret>",
        CompletionList(false, listOf(
          CompletionItem("withoutOwnValues"),
          CompletionItem("withOwnValues").apply {
            commitCharacters = listOf(";")
            data = mapOf("b" to 2)
          },
          CompletionItem("withEmptyValues").apply {
            commitCharacters = emptyList()
            data = linkedMapOf<String, Any>() // not emptyMap(): lsp4j message validator fails on kotlin.collections.EmptyMap
          },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply {
            commitCharacters = listOf(".", "(")
            data = mapOf("a" to 1)
          }
          this.applyKind = applyKind
        }
      )

      assertEquals(listOf(".", "("), items["withoutOwnValues"]!!.commitCharacters)
      assertEquals("""{"a":1}""", items["withoutOwnValues"]!!.data?.toString())
      assertEquals(listOf(";"), items["withOwnValues"]!!.commitCharacters)
      assertEquals("""{"b":2}""", items["withOwnValues"]!!.data?.toString())
      assertEquals(emptyList<String>(), items["withEmptyValues"]!!.commitCharacters)
      assertEquals("{}", items["withEmptyValues"]!!.data?.toString())
    }

    @Test
    fun `commitCharacters are united if applyKind is Merge`(): Unit = timeoutRunBlocking {
      val items = completeAndGetItems(
        "<caret>",
        CompletionList(false, listOf(
          CompletionItem("withoutOwnValues"),
          CompletionItem("withOwnValues").apply { commitCharacters = listOf("(", ";") },
          CompletionItem("withEmptyValues").apply { commitCharacters = emptyList() },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply { commitCharacters = listOf(".", "(") }
          applyKind = CompletionApplyKind().apply { commitCharacters = ApplyKind.Merge }
        }
      )

      assertEquals(listOf(".", "("), items["withoutOwnValues"]!!.commitCharacters)
      assertEquals(listOf(".", "(", ";"), items["withOwnValues"]!!.commitCharacters)
      assertEquals(listOf(".", "("), items["withEmptyValues"]!!.commitCharacters)
    }

    @Test
    fun `data is shallow-merged if applyKind is Merge`(): Unit = timeoutRunBlocking {
      val items = completeAndGetItems(
        "<caret>",
        CompletionList(false, listOf(
          CompletionItem("withoutOwnValues"),
          CompletionItem("withOwnValues").apply { data = mapOf("b" to mapOf("y" to 2), "c" to 3) },
          CompletionItem("withNonObjectValues").apply { data = "string-data" },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply { data = mapOf("a" to 1, "b" to mapOf("x" to 1)) }
          applyKind = CompletionApplyKind().apply { data = ApplyKind.Merge }
        }
      )

      assertEquals("""{"a":1,"b":{"x":1}}""", items["withoutOwnValues"]!!.data?.toString())
      assertEquals("""{"a":1,"b":{"y":2},"c":3}""", items["withOwnValues"]!!.data?.toString())
      assertEquals("\"string-data\"", items["withNonObjectValues"]!!.data?.toString())
    }

    @Test
    fun `merged data is sent in completionItem resolve request`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forRight(CompletionList(false, listOf(
          CompletionItem("myItem").apply { data = mapOf("id" to 42) },
        )).apply {
          itemDefaults = CompletionItemDefaults().apply { data = mapOf("session" to "s1", "id" to 0) }
          applyKind = CompletionApplyKind().apply { data = ApplyKind.Merge }
        })
      }

      serverSession.expectRequest(serverSession.COMPLETION_ITEM_RESOLVE, { it.data?.toString() == """{"session":"s1","id":42}""" }) {
        CompletionItem("myItem").apply { detail = "Resolved detail" }
      }

      val element = codeInsightFixture.completeBasic()!!.single()
      serverSession.awaitExpected()
      val presentation = awaitResolveAndRender(element)

      assertEquals("Resolved detail", presentation.typeText)
    }

    private suspend fun CoroutineScope.completeAndGetItems(
      documentText: String,
      serverResponse: CompletionList,
    ): Map<String, CompletionItem> {
      val virtualFile = codeInsightFixture.configureByText("test.txt", documentText).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forRight(serverResponse)
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      return lookupElements!!.map { (it.`object` as LspCompletionObject).completionItem }.associateBy { it.label }
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

  @Nested
  inner class Cancellation {
    @Test
    fun `cancelled completion sends cancelRequest to the server`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      val pendingResponse = CompletableFuture<Either<List<CompletionItem>, CompletionList>>()
      val requestArrived = serverSession.expectRequestAsync(
        serverSession.COMPLETION,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) { pendingResponse }

      val requestExecutor = LspClientManagerImpl.getInstanceImpl(project)
        .getClients(FakeLspIntegrationProvider::class.java).first().requestExecutor

      val indicator = EmptyProgressIndicator()
      val caller = launch(Dispatchers.IO) {
        try {
          ProgressManager.getInstance().runProcess(
            { runReadActionBlocking { requestExecutor.getCompletionList(virtualFile, 0, false) } },
            indicator,
          )
        }
        catch (_: ProcessCanceledException) {
          // expected: the caller got cancelled while the server response was pending
        }
      }

      requestArrived.await()
      indicator.cancel()

      waitUntilAssertSucceeds(message = "the client must cancel the abandoned completion via $/cancelRequest") {
        assertTrue(pendingResponse.isCancelled)
      }
      caller.join()
    }
  }
}
