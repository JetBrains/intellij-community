// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.api.CommandCompletionFactory
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.ml.MLWeigherUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons.Actions.Lightning
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EmptySoftWrapModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class CommandCompletionProvider : CompletionProvider<CompletionParameters?>() {

  companion object {
    private val LOG = logger<CommandCompletionProvider>()
  }

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    resultSet: CompletionResultSet,
  ) {
    if(!Registry.`is`("java.completion.command.enabled")) return
    resultSet.runRemainingContributors(parameters) {
      resultSet.passResult(it)
    }

    //not support injected fragment, it is not so obvious how to do it right now
    if (parameters.editor is EditorWindow) return
    if (parameters.originalFile.virtualFile is LightVirtualFile) return
    if (parameters.completionType != CompletionType.BASIC) return
    if (DumbService.getInstance(parameters.editor.project ?: return).isDumb) return
    val service = parameters.editor.project?.getService(CommandCompletionService::class.java)
    if (service == null) return
    val commandCompletionFactory = service.getFactory(parameters.position.language)
    if (commandCompletionFactory == null) return
    val offset = parameters.offset
    if (offset == 0) return // we need some context to work with

    if (parameters.editor.document.textLength != offset &&
        StringUtil.isJavaIdentifierPart(parameters.editor.document.immutableCharSequence[offset])) {
      return
    }

    val commandCompletionType = findCommandCompletionType(commandCompletionFactory, parameters) ?: return
    val adjustedParameters = adjustParameters(parameters, commandCompletionType) ?: return
    if (!commandCompletionFactory.isApplicable(adjustedParameters.copyFile, adjustedParameters.offset)) return

    val copyEditor = MyEditor(adjustedParameters.copyFile, parameters.editor.settings)
    copyEditor.caretModel.moveToOffset(adjustedParameters.offset)

    val withPrefixMatcher = resultSet.withPrefixMatcher(CamelHumpMatcher(resultSet.prefixMatcher.prefix, false, true))
      .withRelevanceSorter(createSorter(parameters))

    withPrefixMatcher.restartCompletionOnPrefixChange(StandardPatterns.string().with(object : PatternCondition<String>("add filter for command completion") {
      override fun accepts(t: String, context: ProcessingContext?): Boolean {
        return commandCompletionType.suffix == "." && t == "."
      }
    }));

    // Fetch commands applicable to the position
    getCommandsForContext(commandCompletionFactory,
                          parameters.originalFile.project,
                          copyEditor,
                          adjustedParameters.offset,
                          adjustedParameters.copyFile,
                          parameters.editor,
                          parameters.offset,
                          parameters.originalFile) { commands ->
      withPrefixMatcher.addAllElements(commands.map { command ->
        var element: LookupElement = CommandCompletionLookupElement(LookupElementBuilder.create(command.name)
                                                                      .withIcon(command.icon)
                                                                      .withInsertHandler(CommandInsertHandler(command))
                                                                      .withBoldness(true),
                                                                    adjustedParameters.offset,
                                                                    commandCompletionFactory.suffix().toString() +
                                                                    (commandCompletionFactory.filterSuffix() ?: ""),
                                                                    command.icon ?: Lightning,
                                                                    command.highlightInfo)
        val priority = command.priority
        PrioritizedLookupElement.withPriority(element, priority?.let { it.toDouble() - 100.0 } ?: -110.0)
      })
      true
    }
  }

  private fun createSorter(completionParameters: CompletionParameters): CompletionSorter {
    var weigher = CompletionService.getCompletionService().emptySorter()
      .weigh(object : LookupElementWeigher("priority", true, false) {
        override fun weigh(element: LookupElement): Comparable<*>? {
          if (element.`as`(CommandCompletionLookupElement::class.java) == null) return 0.0
          return element.`as`(PrioritizedLookupElement::class.java)?.priority ?: 0.0
        }
      })
    val location = CompletionLocation(completionParameters)
    val mlWeigher = MLWeigherUtil.findMLWeigher(location);
    if (mlWeigher != null) {
      weigher = MLWeigherUtil.addWeighersToNonDefaultSorter(weigher, location, "proximity");
      weigher = weigher.weigh(mlWeigher);
    }
    return weigher;
  }

  private fun findCommandCompletionType(
    factory: CommandCompletionFactory,
    parameters: CompletionParameters,
  ): InvocationCommandType? {
    val suffix = factory.suffix().toString() + (factory.filterSuffix() ?: "")
    val offset = parameters.offset
    val text = parameters.editor.document.immutableCharSequence
    var indexOf = findActualIndex(suffix, text, offset)
    if (indexOf == 1) {
      return InvocationCommandType.PartialSuffix(text.substring(offset - indexOf + 1, offset), text.substring(offset - indexOf, offset - indexOf + 1))
    }
    else if (offset - indexOf + 2 <= text.length && text.substring(offset - indexOf, offset - indexOf + 2) == suffix) {
      return InvocationCommandType.FullSuffix(text.substring(offset - indexOf + 2, offset), text.substring(offset - indexOf, offset - indexOf + 2))
    }
    if (indexOf > 0) {
      return InvocationCommandType.PartialSuffix(text.substring(offset - indexOf + 1, offset), text.substring(offset - indexOf, offset - indexOf + 1))
    }
    return null
  }

  private fun getCommandsForContext(
    commandCompletionFactory: CommandCompletionFactory,
    project: Project,
    copyEditor: Editor,
    offset: Int,
    copyFile: PsiFile,
    originalEditor: Editor,
    originalOffset: Int,
    originalFile: PsiFile,
    processor: Processor<in Collection<CompletionCommand>>,
  ) {
    val element = copyFile.findElementAt(offset - 1)
    if (element == null) return
    for (provider in commandCompletionFactory.commandProviders()) {
      try {
        //todo delete parameters
        val commands = provider.getCommands(project, copyEditor, offset, copyFile, originalEditor, originalOffset, originalFile)
        processor.process(commands)
      }
      catch (e: Exception) {
        if (e is ControlFlowException) {
          throw e
        }
        if (e !is CommandCompletionUnsupportedOperationException) {
          LOG.error(e)
        }
      }
    }
  }

  private data class AdjustedCompletionParameters(val copyFile: PsiFile, val offset: Int)

  private fun adjustParameters(parameters: CompletionParameters, commandCompletionType: InvocationCommandType): AdjustedCompletionParameters? {
    val originalFile = parameters.originalFile
    val originalDocument = parameters.editor.document

    // Get the document text up to the start of the command (before dots and command text)
    val offset = parameters.offset
    val adjustedOffset = offset - commandCompletionType.suffix.length - commandCompletionType.pattern.length
    if (adjustedOffset <= 0) return null
    val adjustedText = originalDocument.getText(TextRange(0, adjustedOffset)) + originalDocument.getText(TextRange(offset, originalDocument.textLength))

    val file = PsiFileFactory.getInstance(parameters.editor.project).createFileFromText(originalFile.getName(), originalFile.getLanguage(), adjustedText, true, true)
    return AdjustedCompletionParameters(file, adjustedOffset)
  }
}

@ApiStatus.Internal
class CommandCompletionUnsupportedOperationException
  : UnsupportedOperationException("It's unexpected to invoke this method on a command completion calculating.")

private class MyEditor(psiFileCopy: PsiFile, private val settings: EditorSettings) : ImaginaryEditor(psiFileCopy.project, psiFileCopy.viewProvider.document!!) {
  override fun notImplemented(): RuntimeException = throw CommandCompletionUnsupportedOperationException()

  override fun isViewer(): Boolean = false

  override fun isOneLineMode(): Boolean = false

  override fun getSettings(): EditorSettings {
    return settings
  }

  override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition {
    // No folding support: logicalPos is always the same as visual pos
    return VisualPosition(logicalPos.line, logicalPos.column)
  }

  override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition {
    return LogicalPosition(visiblePos.line, visiblePos.column)
  }

  override fun getSoftWrapModel(): SoftWrapModel = EmptySoftWrapModel()
}


internal sealed interface InvocationCommandType {
  val pattern: String
  val suffix: String

  data class PartialSuffix(override val pattern: String, override val suffix: String) : InvocationCommandType
  data class FullSuffix(override val pattern: String, override val suffix: String) : InvocationCommandType
}

internal fun findActualIndex(suffix: String, text: CharSequence, offset: Int): Int {
  var indexOf = suffix.length
  if (offset > text.length || offset == 0) return 0
  while (indexOf > 0 && text.substring(offset - indexOf, offset) != suffix.substring(0, indexOf)) {
    indexOf--
  }
  //try to find outside
  if (indexOf == 0) {
    val maxPathFind = 30
    for (shift in 2..maxPathFind) {
      if (offset - shift < 0) break
      val ch = text[offset - shift]
      if (!(ch.isLetterOrDigit() || ch in suffix || ch == ' ')) break
      val currentSuffixFiltered = text.substring(offset - shift, offset - shift + suffix.length)
      val currentSuffix = text[offset - shift]
      if (currentSuffixFiltered == suffix || currentSuffix == suffix[0]) {
        if (suffix.length == 2 && suffix.first() == suffix.last() &&
            text.substring(offset - shift - 1, offset - shift + suffix.length - 1) == suffix) {
          indexOf = shift + 1
        }
        else {
          indexOf = shift
        }
        break
      }
      if (ch in suffix) {
        return 0
      }
    }
  }
  return indexOf
}
