// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver

/**
 * Collects nested completions for a JSON schema object.
 * If `[node] == null`, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
internal fun JsonSchemaObject.collectNestedCompletions(
  project: Project,
  node: NestedCompletionsNode?,
  completionPath: SchemaPath?,
  collector: (path: SchemaPath?, schema: JsonSchemaObject) -> Unit,
) {
  collector(completionPath, this) // Breadth first

  node
    ?.children
    ?.filterIsInstance<ChildNode.OpenNode>()
    ?.forEach { (name, childNode) ->
      for (subSchema in findSubSchemasByName(project, name)) {
        subSchema.collectNestedCompletions(project, childNode, completionPath / name, collector)
      }
    }
}

private fun JsonSchemaObject.findSubSchemasByName(project: Project, name: String): Iterable<JsonSchemaObject> =
  JsonSchemaResolver(project, this, JsonPointerPosition().apply { addFollowingStep(name) }).resolve()

internal fun LookupElementBuilder.prefixedBy(path: SchemaPath?, treeWalker: JsonLikePsiWalker): LookupElementBuilder =
  path
    ?.let { path.prefix() + "." + lookupString }
    ?.let { lookupString ->
      withPresentableText(lookupString)
        .withLookupString(lookupString)
        .withInsertHandler { context, element ->
          insertHandler?.handleInsert(context, element) // Perform existing handler.
          context.file.findElementAt(context.startOffset)
            ?.sideEffect { completedElement ->
              createMoveData(treeWalker.findContainingObjectAdapter(completedElement), path.accessor())
                .performMove(treeWalker, context, completedElement)
            }
        }
    } ?: this

private fun NestedCompletionMoveData.performMove(treeWalker: JsonLikePsiWalker, context: InsertionContext, completedElement: PsiElement) {
  performMove(CompletedRange(context.startOffset, context.selectionEndOffset), treeWalker, completedElement, context.editor, context.file)
}

/**
 * We assume to be in a state where the key has been correctly completed without any knowledge of nested completions.
 * So there is 3 things we need to do:
 *  1. We need to move the completed key into the existing nested parent.
 *     Example: [org.jetbrains.yaml.schema.YamlByJsonSchemaHeavyNestedCompletionTest.`test nested completion into property that does not exist yet`]
 *  2. We need to wrap the completed with the non-existing parents:
 *     Example: [org.jetbrains.yaml.schema.YamlByJsonSchemaHeavyNestedCompletionTest.`test nested completion into existing property`]
 *  3. We need to move the selection that was made by the original completion handler back to the same place.
 *
 *  Step 1 xor 2 will be performed
 *  Limitations: TODO: Feel free to improve this <3
 *    - While it's language agnostic, it's currently only tested for YAML treeWalker
 *    - It does not nest into arrays
 *    - It does not nest enum values
 *    - Step 3 will currently not be performed in the case of having multiple carets.
 *    - Step 3 will have unpredictable results if the selection spans across multiple lines
 *
 *  @param completedRange This represents the range that has already been completed **before** any nesting has been performed
 */
private fun NestedCompletionMoveData.performMove(completedRange: CompletedRange,
                                                 treeWalker: JsonLikePsiWalker,
                                                 completedElement: PsiElement,
                                                 editor: Editor,
                                                 file: PsiFile) {
  val caretModel = editor.caretModel
  val text = editor.document.charsSequence

  val oldCaretState = caretModel.tryCaptureCaretState(editor, relativeOffset = completedRange.startOffset)

  val (additionalCaretOffset, fullTextWithoutCorrectingNewline) = createTextWrapper(treeWalker, wrappingPath)
    .wrapText(
      around = text.substring(completedRange.toIntRange()),
      caretOffset = oldCaretState?.relativeCaretPosition,
      destinationIndent = destination?.let { treeWalker.indentOf(it) },
      completedElementIndent = completedElement.manuallyDeterminedIndentIn(text)
                               ?: treeWalker.indentOf(completedElement),
      fileIndent = treeWalker.indentOf(file),
    )

  val startOfLine = completedRange.startOffset.movedToStartOfLine(text)
  val endOfLine = completedRange.endOffsetExclusive.movedToEndOfLine(text)
  val takePrecedingNewline = startOfLine > 0
  val takeSucceedingNewline = !takePrecedingNewline && endOfLine < editor.document.lastIndex
  val fullText = fullTextWithoutCorrectingNewline
    .letIf(takePrecedingNewline) { '\n' + it }
    .letIf(takeSucceedingNewline) { it + '\n' }

  editor.document.applyChangesOrdered(
    documentChangeAt(startOfLine) {
      replaceString(startOfLine - takePrecedingNewline.toInt(), endOfLine + takeSucceedingNewline.toInt(), "")
    },
    documentChangeAt(offsetOfInsertionLine(destination, completedElement.startOffset).movedToStartOfLine(text)) { insertionOffset ->
      insertString(insertionOffset - takePrecedingNewline.toInt(), fullText)
      oldCaretState
        ?.restored(editor, newRelativeOffset = insertionOffset + (additionalCaretOffset ?: 0))
        ?.sideEffect { restoredCaret -> caretModel.caretsAndSelections = listOf(restoredCaret) }
    }
  )
}

internal fun CharSequence.mark(index: Int): String = substring(0, index) + "|" + substring(index)  // TODO: Remove, debugging only!
internal fun CharSequence.mark(vararg indices: Int): String = indices.sortedDescending()  // TODO: Remove, debugging only!
  .fold(toString()) { acc, mark -> acc.mark(mark) } // TODO: Remove, debugging only!

private fun offsetOfInsertionLine(destination: PsiElement?, originOffset: Int): Int = when {
  destination == null -> originOffset // If there is no destination, we insert at the origin
  destination.startOffset > originOffset -> destination.startOffset // Caret is above destination, let's insert at top
  else -> destination.endOffset // Caret is below destination, let's insert at bottom
}

private val Document.lastIndex get() = textLength - 1

/**
 * Wraps this text wrapper around [around]. Additionally, it provides information about how much inserting
 * this text will need to move the caret if it used to be on position [caretOffset].
 *
 * It assumes that [around] is existing text, and it does not need to be considered for the caret offset.
 * (however newlines in [around] will be adjusted according to the indent. These indents might need to be considered for the caret offset)
 * @param caretOffset The position where the caret is relative to the start of [around] or null if there is no caret to consider.
 */
private fun WrappedText?.wrapText(
  around: String,
  caretOffset: Int?,
  destinationIndent: Int?,
  completedElementIndent: Int,
  fileIndent: Int,
): TextWithAdditionalCaretOffset =
  wrapText(
    around,
    caretOffset,
    startIndent = destinationIndent ?: completedElementIndent,
    completedElementIndent = completedElementIndent,
    fileIndent,
  )

private fun WrappedText?.wrapText(
  around: String,
  caretOffset: Int?,
  startIndent: Int,
  completedElementIndent: Int,
  fileIndent: Int,
): TextWithAdditionalCaretOffset =
  textWithoutSuffix(startIndent, fileIndent, caretOffset, completedElementIndent, around)
    .withTextSuffixedBy(getFullSuffix(startIndent, fileIndent))

private fun TextWithAdditionalCaretOffset.withTextSuffixedBy(text: String): TextWithAdditionalCaretOffset =
  copy(text = this.text + text)

private fun PsiElement.manuallyDeterminedIndentIn(text: CharSequence): Int? {
  val offsetOfLineStart = startOffset.movedToStartOfLine(text)
  return offsetOfLineStart.movedToFirstOrNull(text) { !it.isWhitespace() }
    ?.let { offsetOfFirstCharInLine -> offsetOfFirstCharInLine - offsetOfLineStart }
}

/** @return null when the carets are in a state that we cannot capture */
private fun CaretModel.tryCaptureCaretState(editor: Editor, relativeOffset: Int): CapturedCaretState? {
  fun Int.captured() = this - relativeOffset
  fun LogicalPosition.toOffset() = editor.logicalPositionToOffset(this)

  return caretsAndSelections.singleOrNull()?.let { caretState ->
    CapturedCaretState(
      currentCaret.offset.captured(),
      caretState.selectionStart?.toOffset()?.captured(),
      caretState.selectionEnd?.toOffset()?.captured(),
      caretState.visualColumnAdjustment,
    )
  }
}

private fun CapturedCaretState.restored(editor: Editor, newRelativeOffset: Int): CaretState {
  fun Int.restoredLogicalPosition(): LogicalPosition = editor.offsetToLogicalPosition(this + newRelativeOffset)
  return CaretState(
    relativeCaretPosition?.restoredLogicalPosition(),
    visualColumnAdjustment,
    relativeSelectionStart?.restoredLogicalPosition(),
    relativeSelectionEnd?.restoredLogicalPosition(),
  )
}

private fun Int.movedToStartOfLine(text: CharSequence) =
  (this downTo 0).firstOrNull { text[it] == '\n' }
    ?.let { firstIndexBeforeLine -> firstIndexBeforeLine + 1 }
  ?: 0


private inline fun Int.movedToFirstOrNull(text: CharSequence, predicate: (Char) -> Boolean) =
  (this..text.lastIndex).firstOrNull { predicate(text[it]) }

private fun Int.movedToEndOfLine(text: CharSequence) = movedToFirstOrNull(text) { it == '\n' } ?: text.length

private fun createTextWrapper(treeWalker: JsonLikePsiWalker, accessor: List<String>): WrappedText? {
  val objectCloser = treeWalker.defaultObjectValue.getOrNull(1)?.toString() ?: ""
  val objectOpener = (": " + (treeWalker.defaultObjectValue.getOrNull(0)?.toString() ?: "")).trimEnd()
  return accessor.asReversed().fold(null as WrappedText?) { acc, nextName ->
    WrappedText(prefix = treeWalker.quoted(nextName) + objectOpener, acc, suffix = objectCloser)
  }
}

internal fun JsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement =
  path?.let {
    findContainingObjectAdapter(start)
      ?.findChildBy(path.accessor(), offset = 0)
      ?.delegate
  } ?: start

private fun JsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement) =
  start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }

internal tailrec fun JsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): JsonValueAdapter? =
  if(offset > path.lastIndex) this
  else childByName(path[offset])
    ?.asObject
    ?.findChildBy(path, offset + 1)

private fun JsonObjectValueAdapter.childByName(name: String): JsonValueAdapter? =
  propertyList.firstOrNull { it.name == name }
    ?.values
    ?.firstOrNull()

private class WrappedText(val prefix: String, val wrapped: WrappedText?, val suffix: String)

private fun WrappedText?.textWithoutSuffix(
  indent: Int,
  fileIndent: Int,
  caretOffset: Int?,
  /** represents the indent that [body] is based on. Newlines inside body will be always have this indent */
  indentOnWhichBodyIsBased: Int,
  body: String,
): TextWithAdditionalCaretOffset =
  when (this) {
    /** For every newline in the [body], we will need to insert the deltaIndent because the nested completions increased the indent */
    null -> TextWithAdditionalCaretOffset(
      offset = caretOffset?.let {
        // for every newline we insert before the caret, we need to offset the caret later
        body.indicesOf('\n')
          .takeWhile { newLineOffset -> newLineOffset < caretOffset }
          .count()
          .let { numberOfLinesBeforeCaret -> numberOfLinesBeforeCaret * (indent - indentOnWhichBodyIsBased) }
      },
      // We do not want to account for the rest of the text, because it already existed in the document before our changes
      text = body.replace("\n", "\n" + " ".repeat(indent - indentOnWhichBodyIsBased)),
    ).withTextPrefixedBy(" ".repeat(indent))
    else -> wrapped.textWithoutSuffix(indent + fileIndent, fileIndent, caretOffset, indentOnWhichBodyIsBased, body)
      .withTextPrefixedBy(" ".repeat(indent) + prefix + "\n")
  }

private fun WrappedText?.getFullSuffix(indent: Int, fileIndent: Int): String =
  if (this == null || suffix.isEmpty()) ""
  else wrapped.getFullSuffix(indent + fileIndent, fileIndent) + " ".repeat(indent) + suffix + "\n"

private data class CompletedRange(val startOffset: Int, val endOffsetExclusive: Int)

private fun CompletedRange.toIntRange() = startOffset until endOffsetExclusive

private fun String.indicesOf(char: Char) = indices.asSequence().filter { this[it] == char }

/**
 * Is used to carry information around regarding how much the caret needs to be offset to insert this string
 * @param offset null implies that there is no caret to be moved
 */
private data class TextWithAdditionalCaretOffset(val offset: Int?, val text: String)

private fun TextWithAdditionalCaretOffset.withTextPrefixedBy(prefix: String) =
  TextWithAdditionalCaretOffset(offset?.plus(prefix.length), prefix + text)

/**
 * Represents how the completion should be split.
 * [destination] is the existing object where the completion will be inserted
 * [wrappingPath] is the keys in which the completion needs to be wrapped.
 */
private class NestedCompletionMoveData(val destination: PsiElement?, val wrappingPath: List<String>)

private fun createMoveData(objectWhereUserIsWithCursor: JsonObjectValueAdapter?, accessor: List<String>): NestedCompletionMoveData {
  var currentNode: JsonObjectValueAdapter? = objectWhereUserIsWithCursor
  var i = 0
  while (i < accessor.size) {
    val nextNode = currentNode?.childByName(accessor[i])?.asObject
    if (nextNode == null) break
    currentNode = nextNode
    i++
  }

  return NestedCompletionMoveData(
    destination = currentNode?.takeUnless { i == 0 }?.delegate,
    wrappingPath = accessor.subList(i, accessor.size),
  )
}

private class CapturedCaretState(
  val relativeCaretPosition: Int?,
  val relativeSelectionStart: Int?,
  val relativeSelectionEnd: Int?,
  val visualColumnAdjustment: Int,
)

private inline fun <T> T.sideEffect(block: (T) -> Unit): Unit = block(this)
private fun Boolean.toInt(): Int = if (this) 1 else 0
private fun JsonLikePsiWalker.quoted(name: String): String = if (requiresNameQuotes()) """"$name"""" else name

inline fun <R, U : R, T : R> T.letIf(condition: Boolean, block: (T) -> U): R = if (condition) block(this) else this