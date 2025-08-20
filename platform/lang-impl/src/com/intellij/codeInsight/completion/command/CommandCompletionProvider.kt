// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.command.commands.AfterHighlightingCommandProvider
import com.intellij.codeInsight.completion.command.commands.DirectIntentionCommandProvider
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.ml.MLWeigherUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons.Actions.IntentionBulbGrey
import com.intellij.icons.AllIcons.Actions.Lightning
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EmptySoftWrapModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

/**
 * Internal provider for handling command completion in IntelliJ-based editors.
 *
 * This class extends the `CompletionProvider` to provide custom completion suggestions
 * in contexts where command completions are applicable. It is primarily used for adding
 * completions for specific command-based scenarios in supported languages or files.
 *
 */
@ApiStatus.Internal
internal class CommandCompletionProvider : CompletionProvider<CompletionParameters?>() {

  companion object {
    private val LOG = logger<CommandCompletionProvider>()
  }

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    resultSet: CompletionResultSet,
  ) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    if (parameters.completionType != CompletionType.BASIC) return
    if (parameters.position is PsiComment) return
    if (parameters.editor.caretModel.caretCount != 1) return
    //not support injected fragment, it is not so obvious how to do it
    //it can work with errors
    if (parameters.editor is EditorWindow) return

    resultSet.runRemainingContributors(parameters) {
      resultSet.passResult(it)
    }
    val project = parameters.editor.project ?: return
    var editor = parameters.editor
    var isReadOnly = false
    var offset = parameters.offset
    val targetEditor = editor.getUserData(ORIGINAL_EDITOR)
    var originalFile = parameters.originalFile
    if (targetEditor != null) {
      isReadOnly = true
      editor = targetEditor.first
      offset = targetEditor.second
      originalFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) ?: return
    }
    if (offset == 0 && !isReadOnly) return // we need some context to work with
    if (originalFile.virtualFile is LightVirtualFile) {
      val topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(originalFile)
      if (topLevelFile?.virtualFile == null || topLevelFile.virtualFile is LightVirtualFile) {
        return
      }
      val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
      if (topLevelEditor is EditorWindow) {
        return
      }
    }
    val commandCompletionService = project.getService(CommandCompletionService::class.java) ?: return
    val dumbService = DumbService.getInstance(project)
    val commandCompletionFactory = commandCompletionService.getFactory(originalFile.language) ?: return
    if (!dumbService.isUsableInCurrentContext(commandCompletionFactory)) return

    if (editor.document.textLength != offset &&
        StringUtil.isJavaIdentifierPart(editor.document.immutableCharSequence[offset])
    ) {
      return
    }

    val commandCompletionType = findCommandCompletionType(commandCompletionFactory, isReadOnly, offset, parameters.editor) ?: return
    val adjustedParameters = try {
      adjustParameters(commandCompletionFactory, commandCompletionType, editor, originalFile, offset, isReadOnly) ?: return
    }
    catch (_: Exception) {
      return
    }

    if (!commandCompletionFactory.isApplicable(adjustedParameters.copyFile, adjustedParameters.offset)) return

    val copyEditor = MyEditor(adjustedParameters.copyFile, editor.settings)
    copyEditor.caretModel.moveToOffset(adjustedParameters.offset)

    val prefix = commandCompletionType.pattern
    val sorter = createSorter(parameters)
    val withPrefixMatcher = resultSet.withPrefixMatcher(LimitedToleranceMatcher(prefix))
      .withRelevanceSorter(sorter)

    withPrefixMatcher.restartCompletionOnPrefixChange(
      StandardPatterns.string().with(object : PatternCondition<String>("add filter for command completion") {
        override fun accepts(t: String, context: ProcessingContext?): Boolean {
          return !isReadOnly && commandCompletionType.suffix + t ==
                 commandCompletionFactory.suffix() + commandCompletionFactory.filterSuffix().toString() ||
                 isReadOnly && commandCompletionType.suffix + t == commandCompletionFactory.filterSuffix().toString()
        }
      }))

    // Fetch commands applicable to the position
    processCommandsForContext(commandCompletionFactory,
                              originalFile.project,
                              copyEditor,
                              adjustedParameters.offset,
                              adjustedParameters.copyFile,
                              editor,
                              parameters.offset,
                              originalFile,
                              isReadOnly) { commands ->
      commands.forEach { command ->
        CommandCompletionCollector.shown(command::class.java, originalFile.language, commandCompletionType::class.java)
        val lookupElement = createLookupElement(command, adjustedParameters, commandCompletionFactory, prefix)
        val customPrefixMatcher = command.customPrefixMatcher(prefix)
        if (customPrefixMatcher != null) {
          val alwaysShowMatcher = resultSet.withPrefixMatcher(customPrefixMatcher)
            .withRelevanceSorter(sorter)
          alwaysShowMatcher.addElement(lookupElement)
        }
        else {
          withPrefixMatcher.addElement(lookupElement)
        }
      }
      true
    }
  }

  private fun createLookupElement(
    command: CompletionCommand,
    adjustedParameters: AdjustedCompletionParameters,
    commandCompletionFactory: CommandCompletionFactory,
    prefix: String,
  ): LookupElement {
    val presentableName = command.presentableName.replace("_", "").replace("...", "").replace("…", "")
    val additionalInfo = command.additionalInfo ?: ""
    var tailText = ""
    if (additionalInfo.isNotEmpty()) {
      tailText += " ($additionalInfo)"
    }
    val lookupString = presentableName.trim().let {
      if (it.length > 50) {
        it.substring(0, 50) + "\u2026"
      }
      else {
        it
      }
    }
    val synonyms = command.synonyms.toMutableList()
    synonyms.add(lookupString)
    synonyms.addAll(generateSynonyms(synonyms))
    val element: LookupElement = CommandCompletionLookupElement(LookupElementBuilder.create(lookupString)
                                                                  .withLookupStrings(synonyms)
                                                                  .withPresentableText(lookupString)
                                                                  .withTypeText(tailText)
                                                                  .withIcon(command.icon ?: IntentionBulbGrey)
                                                                  .withInsertHandler(CommandInsertHandler(command))
                                                                  .withBoldness(false),
                                                                command,
                                                                adjustedParameters.hostAdjustedOffset,
                                                                commandCompletionFactory.suffix().toString() +
                                                                (commandCompletionFactory.filterSuffix() ?: ""),
                                                                command.icon ?: Lightning,
                                                                command.highlightInfo,
                                                                command.customPrefixMatcher(prefix) == null)
    val priority = command.priority
    return PrioritizedLookupElement.withPriority(element, priority?.let { it.toDouble() - 100.0 } ?: -150.0)
  }

  private fun generateSynonyms(synonyms: MutableList<String>): Collection<String> {
    val result = mutableSetOf<String>()
    for (string in synonyms) {
      val newString = string.trim().filter { it !in setOf('\'', '"', '_', "-") }
      if (newString != string) {
        result.add(newString)
      }
    }
    return result
  }

  private fun createSorter(completionParameters: CompletionParameters): CompletionSorter {
    var weigher = CompletionService.getCompletionService().emptySorter()
      .weigh(object : LookupElementWeigher("priority", true, false) {
        override fun weigh(element: LookupElement): Comparable<*> {
          if (element.`as`(CommandCompletionLookupElement::class.java) == null) return 0.0
          return element.`as`(PrioritizedLookupElement::class.java)?.priority ?: 0.0
        }
      })
    val location = CompletionLocation(completionParameters)
    val mlWeigher = MLWeigherUtil.findMLWeigher(location)
    if (mlWeigher != null) {
      weigher = MLWeigherUtil.addWeighersToNonDefaultSorter(weigher, location, "proximity")
      weigher = weigher.weigh(mlWeigher)
    }
    return weigher
  }

  private fun processCommandsForContext(
    commandCompletionFactory: CommandCompletionFactory,
    project: Project,
    copyEditor: Editor,
    offset: Int,
    copyFile: PsiFile,
    originalEditor: Editor,
    originalOffset: Int,
    originalFile: PsiFile,
    isReadOnly: Boolean,
    processor: Processor<in Collection<CompletionCommand>>,
  ) {
    val element = copyFile.findElementAt(offset - 1) ?: return
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    val commandProviders = commandCompletionFactory.commandProviders(project, element.language)
    val afterHighlightingCommandProviders = commandProviders.filter { it is AfterHighlightingCommandProvider }.toSet()
    for (provider in commandProviders.filter { it !is AfterHighlightingCommandProvider }) {
      try {
        if (provider is DirectIntentionCommandProvider) {
          provider.setAfterHighlightingProviders(afterHighlightingCommandProviders)
        }
        if (isReadOnly && !provider.supportsReadOnly()) continue
        copyEditor.caretModel.moveToOffset(offset)
        val context = CommandCompletionProviderContext(project, copyEditor, offset, copyFile, originalEditor, originalOffset, originalFile, isReadOnly)
        val commands = provider.getCommands(context)
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

  private data class AdjustedCompletionParameters(val copyFile: PsiFile, val offset: Int, val hostAdjustedOffset: Int)

  private fun adjustParameters(
    commandCompletionFactory: CommandCompletionFactory,
    commandCompletionType: InvocationCommandType,
    editor: Editor,
    originalFile: PsiFile,
    offset: Int,
    isNonWritten: Boolean,
  ): AdjustedCompletionParameters? {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(originalFile.project)
    val topFile = injectedLanguageManager.getTopLevelFile(originalFile)
    val topEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val originalDocument = topEditor.document

    // Get the document text up to the start of the command (before dots and command text)
    val hostOffset = injectedLanguageManager.injectedToHost(originalFile, offset)
    var adjustedOffset = hostOffset - commandCompletionType.suffix.length - commandCompletionType.pattern.length
    if (adjustedOffset <= 0) return null
    val hostAdjustedOffset = adjustedOffset
    val adjustedText = originalDocument.getText(TextRange(0, adjustedOffset)) + originalDocument.getText(
      TextRange(hostOffset, originalDocument.textLength))


    var file = createFile(isNonWritten, originalFile, topFile, commandCompletionFactory, adjustedText, topEditor)

    if (file is PsiFileImpl) {
      file.setOriginalFile(topFile)
    }
    val injectedElement = injectedLanguageManager.findInjectedElementAt(file, adjustedOffset)
    if (injectedElement != null) {
      file = injectedElement.containingFile
      adjustedOffset = (file.fileDocument as? DocumentWindow)?.hostToInjected(adjustedOffset) ?: 0
    }
    return AdjustedCompletionParameters(file, adjustedOffset, hostAdjustedOffset)
  }

  private fun createFile(
    isNonWritten: Boolean,
    originalFile: PsiFile,
    topFile: PsiFile,
    commandCompletionFactory: CommandCompletionFactory,
    adjustedText: String,
    topEditor: Editor,
  ): PsiFile {
    if (isNonWritten) {
      val createdFile = originalFile.copy() as PsiFile
      if (createdFile is PsiFileImpl) {
        createdFile.setOriginalFile(topFile)
      }
      return createdFile
    }

    if (topFile == originalFile) {
      var createdFile = commandCompletionFactory.createFile(originalFile, adjustedText)
      if (createdFile == null) {
        createdFile = PsiFileFactory.getInstance(topEditor.project)
          .createFileFromText(topFile.getName(), topFile.getLanguage(), adjustedText, true, true, false, topFile.virtualFile)
        if (createdFile is PsiFileImpl) {
          createdFile.setOriginalFile(topFile)
        }
      }
      return createdFile
    }
    val createdFile = PsiFileFactory.getInstance(topEditor.project)
      .createFileFromText(topFile.getName(), topFile.getLanguage(), adjustedText, true, true, false, topFile.virtualFile)
    if (createdFile is PsiFileImpl) {
      createdFile.setOriginalFile(topFile)
    }
    return createdFile
  }
}

@ApiStatus.Internal
internal class CommandCompletionUnsupportedOperationException
  : UnsupportedOperationException("It's unexpected to invoke this method on a command completion calculating.")

internal class MyEditor(psiFileCopy: PsiFile, private val settings: EditorSettings) : ImaginaryEditor(psiFileCopy.project,
                                                                                                      psiFileCopy.viewProvider.document!!) {

  override fun getFoldingModel(): FoldingModel {
    return object : FoldingModel{
      override fun addFoldRegion(startOffset: Int, endOffset: Int, placeholderText: String): FoldRegion? = null
      override fun removeFoldRegion(region: FoldRegion) = Unit
      override fun getAllFoldRegions(): Array<out FoldRegion?> = emptyArray()
      override fun isOffsetCollapsed(offset: Int): Boolean = false
      override fun getCollapsedRegionAtOffset(offset: Int): FoldRegion? = null
      override fun getFoldRegion(startOffset: Int, endOffset: Int): FoldRegion? = null
      override fun runBatchFoldingOperation(operation: Runnable, allowMovingCaret: Boolean, keepRelativeCaretPosition: Boolean) {}
    }
  }

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
  data class FullLine(override val pattern: String, override val suffix: String) : InvocationCommandType
}

internal fun findActualIndex(suffix: String, text: CharSequence, offset: Int): Int {
  var indexOf = suffix.length
  if (offset > text.length || offset == 0) return 0
  while (indexOf > 0 && offset - indexOf >= 0 && text.substring(offset - indexOf, offset) != suffix.take(indexOf)) {
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
            offset - shift - 1 >= 0 &&
            text.substring(offset - shift - 1, offset - shift + suffix.length - 1) == suffix
        ) {
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
  if (indexOf == 0) {
    var currentIndex = 1
    while (offset - currentIndex >= 0 && (text[offset - currentIndex].isLetter() ||
                                          text[offset - currentIndex] == ' ' ||
                                          text[offset - currentIndex] == '\'')
    ) {
      currentIndex++
    }
    if (currentIndex <= 1 || offset - currentIndex >= 0 && text[offset - currentIndex] != '\n') return 0
    while (currentIndex >= 0 && offset - currentIndex >= 0 && text[offset - currentIndex].isWhitespace()) {
      currentIndex--
    }
    if (currentIndex >= 0) indexOf = currentIndex
  }
  return indexOf
}

internal fun findCommandCompletionType(
  factory: CommandCompletionFactory,
  isNonWritten: Boolean,
  offset: Int,
  editor: Editor,
): InvocationCommandType? {
  val suffix = factory.suffix().toString() + (factory.filterSuffix() ?: "")
  val text = editor.document.immutableCharSequence
  if (isNonWritten) {
    return InvocationCommandType.FullSuffix("", editor.document.immutableCharSequence.substring(0, editor.caretModel.offset))
  }
  val indexOf = findActualIndex(suffix, text, offset)
  if (offset - indexOf < 0) return null
  if (indexOf == 1 && text[offset - indexOf] == factory.suffix()) {
    //one point
    return InvocationCommandType.PartialSuffix(text.substring(offset - indexOf + 1, offset),
                                               text.substring(offset - indexOf, offset - indexOf + 1))
  }
  //two points
  else if (offset - indexOf + 2 <= text.length && text.substring(offset - indexOf, offset - indexOf + 2) == suffix) {
    return InvocationCommandType.FullSuffix(text.substring(offset - indexOf + 2, offset),
                                            text.substring(offset - indexOf, offset - indexOf + 2))
  }
  if (indexOf > 0 && text.substring(offset - indexOf).startsWith(factory.suffix())) {
    //force call with one point
    return InvocationCommandType.PartialSuffix(text.substring(offset - indexOf + 1, offset),
                                               text.substring(offset - indexOf, offset - indexOf + 1))
  }
  if (indexOf > 0) {
    //full empty line
    return InvocationCommandType.FullLine(text.substring(offset - indexOf, offset), "")
  }
  return null
}

private class LimitedToleranceMatcher(private val myCurrentPrefix: String) : CamelHumpMatcher(myCurrentPrefix, false, true) {
  override fun prefixMatches(element: LookupElement): Boolean {
    if (!super.prefixMatches(element)) return false
    for (lookupString in element.allLookupStrings) {
      val indexOf = lookupString.indexOf(prefix, ignoreCase = true)
      if (indexOf != -1 && indexOf < 3) return true
      val fragments = matchingFragments(lookupString) ?: continue
      for (range in fragments) {
        if (prefix.length != range.length) continue
        if (range.startOffset >= range.endOffset ||
            range.startOffset < 0 || range.startOffset >= (lookupString.length - 1) ||
            range.endOffset < 0 || range.endOffset >= (lookupString.length - 1)) continue
        val matchedFragment = lookupString.substring(range.startOffset, range.endOffset)
        var errors = 0
        for (i in matchedFragment.indices) {
          if (prefix[i] != matchedFragment[i]) errors++
          if (errors > 2) return false
        }
        if (range.startOffset <= 1) return true
        if (!lookupString[range.startOffset].isLowerCase()) return true
        if (!lookupString[range.startOffset - 1].isLetter()) return true
      }
    }
    return false
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher {
    if (prefix == myCurrentPrefix) {
      return this
    }
    return LimitedToleranceMatcher(prefix)
  }
}