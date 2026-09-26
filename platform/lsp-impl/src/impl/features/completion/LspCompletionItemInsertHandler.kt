package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.platform.lsp.util.getLsp4jPosition
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.InsertTextFormat

internal object LspCompletionItemInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, lookupElement: LookupElement) {
    handleAdditionalTextEdits(context, lookupElement)
    (lookupElement as? LookupElementDecorator<*>)?.delegate?.handleInsert(context)
    handleSnippetFormat(context, lookupElement)
    lookupElement.lsp4jCompletionItem?.command?.let { command ->
      val lspClient = lookupElement.lspClient
      val lspCommandsSupport = lspClient?.descriptor?.lspCustomization?.commandsCustomizer as? LspCommandsSupport
      lspCommandsSupport?.executeCommand(lspClient, context.file.virtualFile, command)
    }
  }

  private fun handleAdditionalTextEdits(context: InsertionContext, lookupElement: LookupElement) {
    val edits = lookupElement.lsp4jCompletionItem?.additionalTextEdits ?: return
    val mainEditRange = lookupElement.lsp4jCompletionItem?.textEdit?.map({ it.range }, { it.insert })
    val startPosition = mainEditRange?.start ?: getLsp4jPosition(context.document, context.startOffset)

    // Full handling of additionalTextEdits is complicated
    // because we need to agree offsets returned by the language servers with the built-in IJ Platform completion.
    // For now, it's straightforward to apply offsets that come before the main textEdit offset, since they are not affected by the above.
    edits.filter { edit ->
      edit.range.start.line < startPosition.line ||
      (edit.range.start.line == startPosition.line && edit.range.start.character <= startPosition.character)
    }.let {
      applyTextEdits(context.document, it)
    }
  }

  private fun handleSnippetFormat(context: InsertionContext, lookupElement: LookupElement) {
    if (lookupElement.lsp4jCompletionItem?.insertTextFormat != InsertTextFormat.Snippet) return

    context.document.replaceString(context.startOffset, context.tailOffset, "")
    TemplateManager.getInstance(context.project)
      .runTemplate(
        context.editor,
        SnippetToTemplateConverter(context.project, lookupElement.lookupString)
          .computeTemplate(context)
      )
  }
}

internal class SnippetToTemplateConverter(private val project: Project, private val rawSnippetText: String) {
  internal fun computeTemplate(context: InsertionContext): Template {
    val givenVariables = parseTemplateVariables(context)

    return TemplateManager.getInstance(project)
      .createTemplate("", "", computeTemplateText(hasEndVariableDeclared(givenVariables)))
      .apply {
        createIjTemplateVariables(givenVariables).forEach(::addVariable)
        setInline(false)
      }
  }

  private fun computeTemplateText(hasExplicitEndVariable: Boolean): String {
    val rawTemplateText = replaceGivenVariables()
    return if (hasExplicitEndVariable)
      rawTemplateText
    else
      appendEndVariable(rawTemplateText)
  }

  private fun replaceGivenVariables(): String {
    return TEMPLATE_VARIABLE_REGEX.replace(rawSnippetText) { matchResult ->
      val index = groupValueByIndexOrEmpty(matchResult, *VARIABLE_NUMBER_GROUPS)
                    .toIntOrNull()
                  ?: return@replace ""

      if (index == 0)
        END_VARIABLE
      else
        $$"$$$VARIABLE_NAME_PREFIX$$index$"
    }
  }

  private fun appendEndVariable(templateText: String): String {
    return "$templateText$END_VARIABLE"
  }

  fun computeEffectiveLookup(): String {
    return TEMPLATE_VARIABLE_REGEX.replace(rawSnippetText, "")
  }

  private fun hasEndVariableDeclared(variables: List<TemplateVariable>): Boolean {
    return variables.any { it.index == 0 }
  }

  private fun createIjTemplateVariables(parsedVariables: List<TemplateVariable>): List<Variable> {
    return parsedVariables.asSequence()
      .distinctBy(TemplateVariable::index)
      .sortedBy(TemplateVariable::index)
      .map(::mapTemplateVariable)
      .toList()
  }

  private fun mapTemplateVariable(variable: TemplateVariable): Variable {
    val variableName = "$VARIABLE_NAME_PREFIX${variable.index}"
    val expression = when (variable) {
      is TemplateVariable.Unknown -> ConstantNode("")
      is TemplateVariable.PlaceHolder -> ConstantNode(variable.defaultValue)
      is TemplateVariable.WithCompletion -> ConstantNode("").withLookupStrings(variable.completionVariants)
    }
    return Variable(variableName, expression, expression, true, false)
  }

  private fun parseTemplateVariables(context: InsertionContext): List<TemplateVariable> {
    return TEMPLATE_VARIABLE_REGEX.findAll(rawSnippetText)
      .mapNotNull { computeTemplateVariable(it, context) }
      .filter { it.index != 0 }
      .toList()
  }

  private fun computeTemplateVariable(match: MatchResult, context: InsertionContext): TemplateVariable? {
    val variableNumber = groupValueByIndexOrEmpty(match, *VARIABLE_NUMBER_GROUPS)
                           .toIntOrNull()
                         ?: return null
    val rawPlaceHolder = groupValueByIndexOrEmpty(match, VARIABLE_DEFAULT_VALUE_GROUP)
    return createVariableFromPlaceHolder(variableNumber, rawPlaceHolder, context)
  }

  private fun createVariableFromPlaceHolder(variableNumber: Int, rawPlaceHolder: String, context: InsertionContext): TemplateVariable {
    return when {
      rawPlaceHolder.startsWith('|') && rawPlaceHolder.endsWith('|') -> TemplateVariable.WithCompletion(variableNumber,
                                                                                                        rawPlaceHolder.trim('|').split(','))
      rawPlaceHolder in LSP_VARIABLE_TRANSFORMATIONS -> TemplateVariable.PlaceHolder(variableNumber,
                                                                                     LSP_VARIABLE_TRANSFORMATIONS[rawPlaceHolder]!!(context)
                                                                                     ?: rawPlaceHolder)
      !rawPlaceHolder.contains('|') && !rawPlaceHolder.contains(',') -> TemplateVariable.PlaceHolder(variableNumber, rawPlaceHolder)
      else -> TemplateVariable.Unknown(variableNumber)
    }
  }

  private fun groupValueByIndexOrEmpty(matchResult: MatchResult, vararg groupIndexes: Int): String {
    return groupIndexes.asSequence()
      .firstNotNullOfOrNull { groupIndex ->
        matchResult.groupValues
          .takeIf { it.size > groupIndex }
          ?.get(groupIndex)
          ?.takeIf(String::isNotEmpty)
      }
      .orEmpty()
  }

  private sealed interface TemplateVariable {
    val index: Int

    data class Unknown(override val index: Int) : TemplateVariable
    data class PlaceHolder(override val index: Int, val defaultValue: String) : TemplateVariable
    data class WithCompletion(override val index: Int, val completionVariants: List<String>) : TemplateVariable
  }
}

// Snippets do even have their own language with eBNF; however, it seems enough to parse them with regex for now
// See: https://microsoft.github.io/language-server-protocol/specification/#snippet_syntax
private val TEMPLATE_VARIABLE_REGEX = "\\$\\{(\\d+):?([^{^}]*)}|\\$(\\d+)".toRegex()
private val VARIABLE_NUMBER_GROUPS = intArrayOf(1, 3)
private const val VARIABLE_DEFAULT_VALUE_GROUP = 2
private const val VARIABLE_NAME_PREFIX = "VAR_"
private const val END_VARIABLE = $$"$END$"
private val LSP_VARIABLE_TRANSFORMATIONS = mapOf(
  $$"$TM_CURRENT_LINE" to { context: InsertionContext -> getLineContent(context) },
  $$"$TM_LINE_INDEX" to { context: InsertionContext -> context.editor.caretModel.logicalPosition.line.toString() },
  $$"$TM_LINE_NUMBER" to { context: InsertionContext -> (context.editor.caretModel.logicalPosition.line + 1).toString() },
  $$"$TM_FILENAME" to { context: InsertionContext -> context.editor.getVFile()?.getName() },
  $$"$TM_FILENAME_BASE" to { context: InsertionContext -> context.editor.getVFile()?.nameWithoutExtension },
  $$"$TM_DIRECTORY" to { context: InsertionContext ->
    context.editor.getVFile()?.let { vFile ->
      (if (!vFile.isDirectory()) vFile.getParent() else vFile).toNioPathString()
    }
  },
  $$"$TM_FILEPATH" to { context: InsertionContext -> context.editor.getVFile()?.toNioPathString() }

  //$$"$TM_CURRENT_WORD" to { context: InsertionContext -> }, // IntelliJ removes the completion prefix from InsertionContext.document.text
  // before calling LspCompletionItemInsertHandler.handleInsert(), so it is not possible to follow VS Code behavior for $TM_CURRENT_WORD.

  //$$"$TM_SELECTED_TEXT" to { context: InsertionContext -> }, // In the runtime IntelliJ deletes selected text from the context before
  // LspCompletionItemInsertHandler calls handleInsert() method. The only solution to get it here is to add UserData in
  // LspCompletionContributor to the LookupElement (at this point selected text still exists), but it is a too complex approach for this
  // small feature. So, it could be implemented only by user request.
)

private fun getLineContent(context: InsertionContext): String {
  val document = context.document
  val line = document.getLineNumber(context.tailOffset)
  return document.charsSequence.subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)).toString()
}

private fun Editor.getVFile(): VirtualFile? = FileDocumentManager.getInstance().getFile(getDocument())
private fun VirtualFile.toNioPathString(): String? = this.fileSystem.getNioPath(this)?.toString()

private val LookupElement.lsp4jCompletionItem: CompletionItem?
  get() = (this.`object` as? LspCompletionObject)?.completionItem
          ?: this.`object` as? CompletionItem // for SnippetParsingTest

private val LookupElement.lspClient: LspClientImpl?
  get() = (this.`object` as? LspCompletionObject)?.lspClient
