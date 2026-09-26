package com.intellij.platform.lsp.unit

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.OffsetMap
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.platform.lsp.impl.features.completion.LspCompletionItemInsertHandler
import com.intellij.platform.lsp.impl.features.completion.SnippetToTemplateConverter
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.InsertTextFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class LspSnippetParsingTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeContributor by extensionPointFixture(CompletionContributor.EP) {
    CompletionContributorEP("any", FakeCompletionContributor::class.java.name, DefaultPluginDescriptor("SnippetCompletion"))
  }

  @Test
  fun `snippet recognition`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "<caret>")
    val context = createInsertionContext()

    val converter = SnippetToTemplateConverter(project, $$"Hello ${1: a place for the world}!")
    assertEquals("Hello !", converter.computeEffectiveLookup())
    assertEquals("VAR_1", converter.computeTemplate(context).variables.single().name)
  }

  @Test
  fun `snippet with completion items recognition`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "<caret>")
    val context = createInsertionContext()

    val converter = SnippetToTemplateConverter(project, $$"Hello from IJ ${1:|LSP,language server protocol|}!")

    val completionVariants = converter.computeTemplate(context)
      .variables.single()
      .expression.calculateLookupItems(null)
      ?.map { it.lookupString }
      .orEmpty()
    assertEquals(setOf("LSP", "language server protocol"), completionVariants.toSet())
  }

  @Test
  fun `several snippet kinds in a completion item`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "<caret>")
    val context = createInsertionContext()

    val converter = SnippetToTemplateConverter(project, $$"VAR_1: ${1:|1,2|}, VAR_2: ${2:|3,4|}")
    assertEquals(2, converter.computeTemplate(context).variables.size)
  }

  @Test
  fun `variable zero treated as template end`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "zero index: <caret>")
    codeInsightFixture.completeBasic()
    codeInsightFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    codeInsightFixture.checkResult("zero index: >>><<<")
    assertEquals(15, codeInsightFixture.caretOffset)
  }

  @Test
  fun `order of variables preserved`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("test.txt", "completion order: <caret>")
    codeInsightFixture.completeBasic()
    codeInsightFixture.type("\t")
    codeInsightFixture.type("\n")
    codeInsightFixture.checkResult("""
      completion order: last var-last, first var-first

    """.trimIndent())
    // assert the caret is placed after the snippet since no variable with number==0 was provided
    assertEquals(49, codeInsightFixture.caretOffset)
  }

  @Test
  fun `snippets insert handler in real completion`() = doCompleteAndAssert(
    initialText = "completion available: <caret>",
    expectedText = "completion available: Hello world from lsp"
  )

  @Test
  fun `snippet variable placeholder`() = doCompleteAndAssert(
    initialText = "placeholder: <caret>",
    expectedText = "placeholder: singleValue"
  )

  @Test
  fun `TM_LINE_NUMBER LSP variable`() = doCompleteAndAssert(
    initialText = "line number: <caret>",
    expectedText = "line number: 1"
  )

  @Test
  fun `TM_LINE_INDEX LSP variable`() = doCompleteAndAssert(
    initialText = "line index: <caret>",
    expectedText = "line index: 0"
  )

  @Test
  fun `TM_FILENAME LSP variable`() = doCompleteAndAssert(
    fileName = "fileName.txt",
    initialText = "file name: <caret>",
    expectedText = "file name: fileName.txt",
  )

  @Test
  fun `TM_FILENAME_BASE LSP variable`() = doCompleteAndAssert(
    fileName = "fileNameBase.txt",
    initialText = "file name base: <caret>",
    expectedText = "file name base: fileNameBase",
  )

  @Test
  fun `TM_CURRENT_LINE LSP variable`() = doCompleteAndAssert(
    initialText = "current line: <caret>",
    expectedText = "current line: current line: "
  )

  private fun doCompleteAndAssert(
    fileName: String = "test.txt",
    initialText: String,
    expectedText: String,
    finishChar: Char = Lookup.NORMAL_SELECT_CHAR,
  ) = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText(fileName, initialText)
    codeInsightFixture.completeBasic()
    codeInsightFixture.finishLookup(finishChar)
    codeInsightFixture.checkResult(expectedText)
  }

  private fun createInsertionContext(completionChar: Char = Lookup.NORMAL_SELECT_CHAR): InsertionContext {
    val editor = codeInsightFixture.editor
    val psiFile = codeInsightFixture.file

    val offsetMap = OffsetMap(editor.document)
    val context = InsertionContext(
      offsetMap,
      completionChar,
      LookupElement.EMPTY_ARRAY,
      psiFile,
      editor,
      InsertionContext.shouldAddCompletionChar(completionChar)
    )

    val caretOffset = editor.caretModel.offset
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, caretOffset)
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, caretOffset)
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, caretOffset)

    return context
  }

  private class FakeCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      val effectiveInsertText = when (CompletionUtil.getOriginalOrSelf(parameters.position).text) {
        "completion available: " -> $$"Hello ${2:|world,jetbrains|} from ${1:|lsp|}"
        "completion order: " -> $$"last var-${2:|last|}, first var-${1:|first|}"
        "placeholder: " -> $$"${1:singleValue}"
        "zero index: " -> $$">>>$0<<<"
        "line number: " -> $$"${1:$TM_LINE_NUMBER}"
        "line index: " -> $$"${1:$TM_LINE_INDEX}"
        "file name: " -> $$"${1:$TM_FILENAME}"
        "file name base: " -> $$"${1:$TM_FILENAME_BASE}"
        "current line: " -> $$"${1:$TM_CURRENT_LINE}"
        "directory: " -> $$"${1:$TM_DIRECTORY}"
        "file path: " -> $$"${1:$TM_FILEPATH}"
        else -> "unknown context completion"
      }

      val completionItem = CompletionItem().apply {
        insertTextFormat = InsertTextFormat.Snippet
        insertText = effectiveInsertText
      }
      result.addElement(
        LookupElementBuilder
          .create(completionItem, completionItem.insertText!!)
          .withInsertHandler(LspCompletionItemInsertHandler)
      )
    }
  }
}