// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionResult.SHOULD_NOT_CHECK_WHEN_WRAP
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.completion.command.commands.AfterHighlightingCommandProvider
import com.intellij.codeInsight.completion.command.commands.DirectIntentionCommandProvider
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.ml.MLWeigherUtil
import com.intellij.codeInsight.completion.serialization.PrefixMatcherDescriptor
import com.intellij.codeInsight.completion.serialization.PrefixMatcherDescriptorConverter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.icons.AllIcons.Actions.IntentionBulbGrey
import com.intellij.icons.AllIcons.Actions.Lightning
import com.intellij.idea.AppMode
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EmptySoftWrapModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.FORCE_INJECTED_COPY_ELEMENT_KEY
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.FORCE_INJECTED_EDITOR_KEY
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ProcessingContext
import com.intellij.util.Processor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable

private const val STANDARD_MEAN_PRIORITY = 100.0

private const val DEFAULT_PRIORITY = -150.0

private val CHAR_TO_FILTER = setOf('\'', '"', '_', '-')

private val CHAR_TO_FILTER_WITH_SPACE = setOf('\'', '"', '_', '-', ' ')

/**
 * Internal provider for handling command completion in IntelliJ-based editors.
 *
 * This class extends the `CompletionProvider` to provide custom completion suggestions
 * in contexts where command completions are applicable. It is primarily used for adding
 * completions for specific command-based scenarios in supported languages or files.
 *
 */
@ApiStatus.Internal
internal class CommandCompletionProvider(val contributor: CommandCompletionContributor) : CompletionProvider<CompletionParameters?>() {

  companion object {
    private val LOG = logger<CommandCompletionProvider>()
  }

  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    resultSet: CompletionResultSet,
  ) {
    if (!AppMode.isRemoteDevHost() && AppMode.isHeadless() &&
        !(ApplicationManager.getApplication().isUnitTestMode() && Registry.`is`("ide.completion.command.force.enabled", false))) return
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    if (parameters.completionType != CompletionType.BASIC) return
    if (parameters.position is PsiComment) return
    if (parameters.editor.caretModel.caretCount != 1) return
    val templateState = TemplateManagerImpl.getTemplateState(parameters.editor)
    if (templateState != null && !templateState.isFinished) return
    val postfixResults = mutableListOf<CompletionResult>()
    resultSet.runRemainingContributors(parameters) { r: CompletionResult ->
      if (r.lookupElement.`as`(PostfixTemplateLookupElement::class.java) != null) {
        postfixResults.add(r)
      }
      else {
        resultSet.passResult(r)
      }
    }
    try {
      addCommandCompletions(parameters, resultSet, postfixResults)
    }
    finally {
      for (value in postfixResults) {
        resultSet.passResult(value)
      }
    }
  }

  private fun addCommandCompletions(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
    postfixResults: MutableList<CompletionResult>,
    ) {
    enableFastShown(parameters)
    val project = parameters.editor.project ?: return
    var editor = parameters.editor
    var isReadOnly = false
    var isInjected = false
    var offset = parameters.editor.caretModel.offset
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
      if (editor != topLevelEditor) {
        isInjected = true
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
    if (commandCompletionType !is InvocationCommandType.FullSuffix) {
      for (completionResult in postfixResults) {
        resultSet.passResult(completionResult)
      }
      postfixResults.clear()
    }
    val adjustedParameters = try {
      adjustParameters(commandCompletionFactory, commandCompletionType, editor, originalFile, offset, isReadOnly, isInjected) ?: return
    }
    catch (_: Exception) {
      return
    }

    if (isInjected) {
      if (adjustedParameters.injectedFile == null) return
      if (adjustedParameters.injectedOffset == null) return
      if (!commandCompletionFactory.isApplicable(adjustedParameters.injectedFile, adjustedParameters.injectedOffset)) return
      val originalCompletionFactory = commandCompletionService.getFactory(adjustedParameters.copyFile.language) ?: return
      if (!originalCompletionFactory.isApplicableForHost(adjustedParameters.copyFile, adjustedParameters.copyOffset)) return
    }
    else {
      if (!commandCompletionFactory.isApplicable(adjustedParameters.copyFile, adjustedParameters.copyOffset)) return
    }

    val copyEditor = MyEditor(adjustedParameters.copyFile, editor.settings)
    copyEditor.caretModel.moveToOffset(adjustedParameters.copyOffset)

    val prefix = commandCompletionType.pattern
    val sorter = createSorter(parameters)
    val withRelevanceSorter = resultSet.withRelevanceSorter(sorter)

    resultSet.restartCompletionOnPrefixChange(
      StandardPatterns.string().with(object : PatternCondition<String>("add filter for command completion") {
        override fun accepts(prefix: String, context: ProcessingContext?): Boolean {
          val fullSuffix = commandCompletionFactory.suffix() + (commandCompletionFactory.filterSuffix()?.toString() ?: "")
          return (!isReadOnly &&
                  (commandCompletionType.suffix + prefix == fullSuffix) || prefix.endsWith(fullSuffix)) ||
                 (isReadOnly && (commandCompletionType.suffix + prefix == (commandCompletionFactory.filterSuffix()?.toString() ?: "")))
        }
      }))

    // Fetch commands applicable to the position
    processCommandsForContext(commandCompletionFactory = commandCompletionFactory,
                              project = originalFile.project,
                              copyEditor = copyEditor,
                              adjustedParameters = adjustedParameters,
                              originalEditor = editor,
                              originalOffset = offset,
                              originalFile = originalFile,
                              isReadOnly = isReadOnly,
                              isInjected = isInjected,
                              commandCompletionType = commandCompletionType) { commands ->
      val baseMatcher = CamelHumpMatcher(prefix, false, true)
      commands.forEach { command ->
        ProgressManager.checkCanceled()
        CommandCompletionCollector.shown(command::class.java, originalFile.language, commandCompletionType::class.java)
        val customPrefixMatcher = command.customPrefixMatcher(prefix)
        val lookupElements = createLookupElements(command, commandCompletionFactory, prefix, customPrefixMatcher)
        if (customPrefixMatcher != null) {
          val alwaysShowMatcher = resultSet.withPrefixMatcher(customPrefixMatcher)
            .withRelevanceSorter(sorter)
          for (element in lookupElements) {
            alwaysShowMatcher.addElement(element)
          }
        }
        else {
          for (element in lookupElements) {
            if (!baseMatcher.prefixMatches(element)) continue
            val commandCompletionElement = element.`as`(CommandCompletionLookupElement::class.java)!!
            val matcher = LimitedToleranceMatcher(prefix, commandCompletionElement.currentTags, commandCompletionElement.otherTags)
            element.putUserData(SHOULD_NOT_CHECK_WHEN_WRAP, true)
            withRelevanceSorter.withPrefixMatcher(matcher).addElement(element)
          }
        }
      }
      true
    }
  }

  private fun enableFastShown(parameters: CompletionParameters) {
    if (Registry.`is`("ide.completion.command.faster.paint")) {
      if (!GroupedCompletionContributor.isGroupEnabledInApp()) return
      if (!contributor.groupIsEnabled(parameters)) return
      val completionProgressIndicator = parameters.process as? CompletionProgressIndicator
      val count = completionProgressIndicator?.lookup?.list?.model?.size ?: 0
      //just to avoid irritating flickering without items
      if (count > 0 &&
          (prevVisibleLeaf(parameters.position) as? PsiWhiteSpace)?.textContains('\n') == true) {
        completionProgressIndicator?.unfreezeAndShowLookupAsSoonAsPossible()
      }
    }
  }

  private fun prevVisibleLeaf(element: PsiElement): PsiElement? {
    var prevLeaf = PsiTreeUtil.prevLeaf(element, true)
    while (prevLeaf != null && StringUtil.isEmpty(prevLeaf.getText())) prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf, true)
    return prevLeaf
  }

  private fun createLookupElements(
    command: CompletionCommand,
    commandCompletionFactory: CommandCompletionFactory,
    prefix: String,
    customPrefixMatcher: PrefixMatcher?,
  ): List<LookupElement> {
    var presentableName = command.presentableName
      .removeSuffix("...")
      .removeSuffix("…")
    if (command is ActionCompletionCommand) {
      presentableName = presentableName.replace("_", "")
    }
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
    synonyms.remove(lookupString)
    synonyms.addFirst(lookupString)
    if (customPrefixMatcher != null) {
      val element: LookupElement = createElement(
        lookupString = lookupString,
        lookupStrings = synonyms,
        tailText = tailText,
        command = command,
        commandCompletionFactory = commandCompletionFactory,
        prefix = prefix,
        currentSynonyms = emptyList(),
        otherSynonyms = emptyList()
      )
      val priority = command.priority
      return listOf(PrioritizedLookupElement.withPriority(element, priority?.let { it.toDouble() - STANDARD_MEAN_PRIORITY } ?: DEFAULT_PRIORITY))
    }
    val elements = mutableListOf<LookupElement>()
    for (synonym in synonyms) {
      val currentSynonyms = mutableListOf(synonym)
      currentSynonyms.addAll(generateSynonyms(currentSynonyms))
      val element: LookupElement = createElement(
        lookupString = lookupString,
        lookupStrings = currentSynonyms,
        tailText = tailText,
        command = command,
        commandCompletionFactory = commandCompletionFactory,
        prefix = prefix,
        currentSynonyms = currentSynonyms,
        otherSynonyms = synonyms
      )
      val priority = command.priority
      elements.add(PrioritizedLookupElement.withPriority(element, priority?.let { it.toDouble() - STANDARD_MEAN_PRIORITY } ?: DEFAULT_PRIORITY))
    }
    return elements
  }

  private fun createElement(lookupString: String,
                            lookupStrings: List<String>,
                            tailText: String,
                            command: CompletionCommand,
                            commandCompletionFactory: CommandCompletionFactory,
                            prefix: String,
                            currentSynonyms: List<String>,
                            otherSynonyms: List<String>): CommandCompletionLookupElement {
    return CommandCompletionLookupElement(lookupElement =
                                            LookupElementBuilder.create(lookupString)
                                              .withLookupStrings(lookupStrings)
                                              .withPresentableText(lookupString)
                                              .withTypeText(tailText)
                                              .withIcon(command.icon ?: IntentionBulbGrey)
                                              .withInsertHandler(CommandInsertHandler(command))
                                              .withBoldness(false),
                                          command = command,
                                          suffix = commandCompletionFactory.suffix().toString() +
                                                   (commandCompletionFactory.filterSuffix() ?: ""),
                                          icon = command.icon ?: Lightning,
                                          highlighting = command.highlightInfo,
                                          useLookupString = command.customPrefixMatcher(prefix) == null,
                                          currentTags = currentSynonyms,
                                          otherTags = otherSynonyms)
  }

  private fun generateSynonyms(synonyms: MutableList<String>): Collection<String> {
    val result = mutableSetOf<String>()
    for (string in synonyms) {
      var newString = string.trim().filter { it !in CHAR_TO_FILTER }
      if (newString != string) {
        result.add(newString)
      }
      newString = string.trim().filter { it !in CHAR_TO_FILTER_WITH_SPACE }
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
    adjustedParameters: AdjustedCompletionParameters,
    originalEditor: Editor,
    originalOffset: Int,
    originalFile: PsiFile,
    isReadOnly: Boolean,
    isInjected: Boolean,
    commandCompletionType: InvocationCommandType,
    processor: Processor<in Collection<CompletionCommand>>,
  ) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    val language = (adjustedParameters.injectedFile ?: adjustedParameters.copyFile).language
    val allCommandProviders = commandCompletionFactory.commandProviders(project, language)

    val (afterHighlightingCommandProviderList, commandProviders) = allCommandProviders.partition { it is AfterHighlightingCommandProvider }
    val afterHighlightingCommandProviders = afterHighlightingCommandProviderList.toSet()

    for (provider in commandProviders) {
      if (commandCompletionType is InvocationCommandType.FullLine && !provider.supportNewLineCompletion()) continue
      try {
        if (provider is DirectIntentionCommandProvider) {
          provider.setAfterHighlightingProviders(afterHighlightingCommandProviders)
        }
        if (isReadOnly && !provider.supportsReadOnly()) continue
        if (isInjected && !provider.supportsInjected()) continue
        copyEditor.caretModel.moveToOffset(adjustedParameters.copyOffset)
        val context =
          if (adjustedParameters.injectedFile != null && adjustedParameters.injectedOffset != null && isInjected) {
            val injectedEditor = createInjectedEditor(adjustedParameters.injectedFile, copyEditor, copyEditor.settings) ?: return
            injectedEditor.caretModel.moveToOffset(adjustedParameters.injectedOffset)
            CommandCompletionProviderContext(project, injectedEditor, adjustedParameters.injectedOffset, adjustedParameters.injectedFile, originalEditor, originalOffset, originalFile, isReadOnly, isInjected)
          }
          else {
            CommandCompletionProviderContext(project, copyEditor, adjustedParameters.copyOffset, adjustedParameters.copyFile, originalEditor, originalOffset, originalFile, isReadOnly, isInjected)
          }
        val commands = provider.getCommands(context)
        processor.process(commands)

        ProgressManager.checkCanceled()
      }
      catch (e: Exception) {
        if (e is ControlFlowException) {
          throw e
        }
        if (e !is CommandCompletionUnsupportedOperationException) {
          //it was rethrown before
          @Suppress("IncorrectCancellationExceptionHandling")
          LOG.error(e)
        }
      }
    }
  }

  private data class AdjustedCompletionParameters(
    val copyFile: PsiFile,
    val injectedFile: PsiFile?,
    val copyOffset: Int,
    val injectedOffset: Int?,
  )

  private fun adjustParameters(
    commandCompletionFactory: CommandCompletionFactory,
    commandCompletionType: InvocationCommandType,
    editor: Editor,
    originalFile: PsiFile,
    offset: Int,
    isNonWritten: Boolean,
    isInjected: Boolean,
  ): AdjustedCompletionParameters? {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(originalFile.project)
    val topFile = injectedLanguageManager.getTopLevelFile(originalFile)
    val topEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val originalDocument = topEditor.document

    // Get the document text up to the start of the command (before dots and command text)
    val hostOffset = injectedLanguageManager.injectedToHost(originalFile, offset)
    val moveRange = commandCompletionType.suffix.length + commandCompletionType.pattern.length
    val adjustedOffset = hostOffset - moveRange
    if (adjustedOffset <= 0) return null
    val adjustedText = originalDocument.getText(TextRange(0, adjustedOffset)) + originalDocument.getText(
      TextRange(hostOffset, originalDocument.textLength))


    val copyTopFile =
      if (!isInjected) {
        createFile(isNonWritten, originalFile, topFile, commandCompletionFactory, adjustedText, topEditor) ?: return null
      }
      else {
        val createdFile = topFile.copy() as PsiFile
        val injectedElementAt = injectedLanguageManager.findInjectedElementAt(createdFile, adjustedOffset) ?: return null
        val injectionHost = injectedLanguageManager.getInjectionHost(injectedElementAt) ?: return null
        val injectedFileDocument = injectedElementAt.containingFile?.fileDocument as? DocumentWindow ?: return null
        val hostToInjectedStartOffset = injectedFileDocument.hostToInjected(adjustedOffset)
        injectedFileDocument.deleteString(hostToInjectedStartOffset, hostToInjectedStartOffset + moveRange)
        PsiDocumentManager.getInstance(createdFile.project).commitDocument(injectedFileDocument)
        if (!injectionHost.isValid) return null
        val originalInjectedHost = injectedLanguageManager.getInjectionHost(originalFile)
        injectionHost.putCopyableUserData(FORCE_INJECTED_COPY_ELEMENT_KEY, originalInjectedHost)
        createdFile
      }
    if (copyTopFile is PsiFileImpl) {
      copyTopFile.setOriginalFile(topFile)
    }
    val injectedElement = injectedLanguageManager.findInjectedElementAt(copyTopFile, adjustedOffset)
    var injectedOffset: Int? = null
    if (injectedElement != null) {
      injectedOffset = (injectedElement.containingFile.fileDocument as? DocumentWindow)?.hostToInjected(adjustedOffset) ?: 0
    }
    val injectedFile = injectedElement?.containingFile
    if ((injectedOffset == null || injectedFile == null) && isInjected) {
      return null
    }
    return AdjustedCompletionParameters(copyTopFile, injectedFile, adjustedOffset, injectedOffset)
  }

  private fun createFile(
    isNonWritten: Boolean,
    originalFile: PsiFile,
    topFile: PsiFile,
    commandCompletionFactory: CommandCompletionFactory,
    adjustedText: String,
    topEditor: Editor,
  ): PsiFile? {
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
      }
      if (createdFile is PsiFileImpl) {
        createdFile.setOriginalFile(topFile)
      }
      return createdFile
    }
    return null
  }
}

@ApiStatus.Internal
internal class CommandCompletionUnsupportedOperationException
  : UnsupportedOperationException("It's unexpected to invoke this method on a command completion calculating.")

internal open class MyEditor(psiFileCopy: PsiFile, private val settings: EditorSettings) : ImaginaryEditor(psiFileCopy.project,
                                                                                                           psiFileCopy.viewProvider.document!!) {

  override fun getFoldingModel(): FoldingModel {
    return object : FoldingModel {
      override fun addFoldRegion(startOffset: Int, endOffset: Int, placeholderText: String): FoldRegion? = null
      override fun removeFoldRegion(region: FoldRegion) = Unit
      override fun getAllFoldRegions(): Array<out FoldRegion> = emptyArray()
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
    while (text.getOrNull(offset - currentIndex)?.isLetter() == true ||
           text.getOrNull(offset - currentIndex) == ' ' ||
           text.getOrNull(offset - currentIndex) == '\''
    ) {
      currentIndex++
    }
    if (currentIndex <= 1 || text.getOrNull(offset - currentIndex) != '\n') return 0
    while (currentIndex >= 0 && text.getOrNull(offset - currentIndex)?.isWhitespace() == true) {
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
  else if (offset - indexOf + 2 <= text.length &&
           offset >= offset - indexOf + 2 &&
           text.substring(offset - indexOf, offset - indexOf + 2) == suffix) {
    return InvocationCommandType.FullSuffix(text.substring(offset - indexOf + 2, offset),
                                            text.substring(offset - indexOf, offset - indexOf + 2))
  }
  if (indexOf > 0 &&
      text.substring(offset - indexOf).startsWith(factory.suffix())) {
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

internal class LimitedToleranceMatcher(
  prefix: String,
  private val currentTags: List<String>,
  private val otherTags: List<String>
) : CamelHumpMatcher(prefix, false, true) {

  override fun prefixMatches(element: LookupElement): Boolean {
    if (!super.prefixMatches(element)) return false
    val allLookupStrings = this.currentTags.ifEmpty { element.allLookupStrings }
    if (!matched(allLookupStrings)) return false
    if (this.otherTags.isEmpty()) return true
    val indexOfFirst = this.otherTags.indexOfFirst { allLookupStrings.contains(it) }
    if (indexOfFirst <= 0) return true
    if (matched(this.otherTags.subList(0, indexOfFirst))) return false
    return true
  }

  private fun matched(allLookupStrings: @Unmodifiable Collection<String>): Boolean {
    for (lookupString in allLookupStrings) {
      val indexOf = lookupString.indexOf(prefix, ignoreCase = true)
      if (indexOf != -1 && indexOf < 3) return true
      val fragments = matchingFragments(lookupString) ?: continue
      for (range in fragments) {
        if (prefix.length != range.length) continue
        if (range.startOffset >= range.endOffset ||
            range.startOffset < 0 || range.startOffset >= lookupString.length - 1 ||
            range.endOffset < 0 || range.endOffset > lookupString.length) continue
        val matchedFragment = lookupString.substring(range.startOffset, range.endOffset)
        var errors = 0
        for (i in matchedFragment.indices) {
          if (prefix[i] != matchedFragment[i]) errors++
          if (errors > 2) continue
        }
        if (range.startOffset <= 1) return true
        if (!lookupString[range.startOffset].isLowerCase()) return true
        if (!lookupString[range.startOffset - 1].isLetter()) return true
      }
    }
    return false
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher {
    if (prefix == this.prefix) {
      return this
    }
    return LimitedToleranceMatcher(prefix, currentTags, otherTags)
  }

  class Converter : PrefixMatcherDescriptorConverter<LimitedToleranceMatcher> {
    override fun toDescriptor(target: LimitedToleranceMatcher): PrefixMatcherDescriptor =
      Descriptor(target.prefix, target.currentTags, target.otherTags)
  }

  @Serializable
  data class Descriptor(
    private val prefix: String,
    private val currentTags: List<String>,
    private val otherTags: List<String>,
  ) : PrefixMatcherDescriptor {
    override fun recreateMatcher(): PrefixMatcher =
      LimitedToleranceMatcher(prefix, currentTags, otherTags)
  }
}

internal fun createInjectedEditor(
  psiFile: PsiFile,
  myEditor: Editor,
  editorSettings: EditorSettings,
): EditorWindow? {
  val documentWindow = psiFile.fileDocument as? DocumentWindow ?: return null

  val injectedEditor = object : MyEditor(psiFile, editorSettings), EditorWindow {
    override fun isValid(): Boolean {
      return true
    }

    override fun getDocument(): DocumentWindow {
      return documentWindow
    }

    override fun getInjectedFile(): PsiFile {
      return psiFile
    }

    override fun hostToInjected(hPos: LogicalPosition): LogicalPosition {
      val offset = myEditor.logicalPositionToOffset(hPos)
      val hostToInjected = documentWindow.hostToInjected(offset)
      return offsetToLogicalPosition(hostToInjected)
    }

    override fun injectedToHost(pos: LogicalPosition): LogicalPosition {
      val offset = logicalPositionToOffset(pos)
      return delegate.offsetToLogicalPosition(documentWindow.injectedToHost(offset))
    }

    override fun getDelegate(): Editor {
      return myEditor
    }
  }
  myEditor.putUserData(FORCE_INJECTED_EDITOR_KEY, injectedEditor)
  return injectedEditor
}