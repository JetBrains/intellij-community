// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.ml.MLWeigherUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
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
    if (!Registry.`is`("ide.completion.command.enabled")) return
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
    if (parameters.completionType != CompletionType.BASIC) return
    val commandCompletionService = project.getService(CommandCompletionService::class.java)
    if (commandCompletionService == null) return
    val dumbService = DumbService.getInstance(project)
    val commandCompletionFactory = commandCompletionService.getFactory(originalFile.language)
    if (commandCompletionFactory == null) return
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
    val withPrefixMatcher = resultSet.withPrefixMatcher(CamelHumpMatcher(prefix, false, true))
      .withRelevanceSorter(createSorter(parameters))


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
      withPrefixMatcher.addAllElements(commands.map { command ->
        val i18nName = command.i18nName.replace("_", "").replace("...", "").replace("…", "")
        val additionalInfo = command.additionalInfo ?: ""
        var tailText = if (command.name.equals(i18nName, ignoreCase = true)) "" else " $i18nName"
        if (additionalInfo.isNotEmpty()) {
          tailText += " ($additionalInfo)"
        }
        CommandCompletionCollector.shown(command::class.java, originalFile.language, commandCompletionType::class.java)
        val element: LookupElement = CommandCompletionLookupElement(LookupElementBuilder.create(command.name.trim())
                                                                      .withLookupString(i18nName.trim())
                                                                      .withTypeText(tailText)
                                                                      .withIcon(command.icon ?: Lightning)
                                                                      .withInsertHandler(CommandInsertHandler(command))
                                                                      .withBoldness(true),
                                                                    adjustedParameters.hostAdjustedOffset,
                                                                    commandCompletionFactory.suffix().toString() +
                                                                    (commandCompletionFactory.filterSuffix() ?: ""),
                                                                    command.icon ?: Lightning,
                                                                    command.highlightInfo)
        val priority = command.priority
        PrioritizedLookupElement.withPriority(element, priority?.let { it.toDouble() - 100.0 } ?: -150.0)
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
    val element = copyFile.findElementAt(offset - 1)
    if (element == null) return
    for (provider in commandCompletionFactory.commandProviders(project, element.language)) {
      try {
        if (isReadOnly && !provider.supportsReadOnly()) continue
        val commands = provider.getCommands(
          CommandCompletionProviderContext(project, copyEditor, offset, copyFile, originalEditor, originalOffset, originalFile, isReadOnly))
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


    var file =
      if (isNonWritten) {
        val createdFile = originalFile.copy() as PsiFile
        if (createdFile is PsiFileImpl) {
          createdFile.setOriginalFile(topFile)
        }
        createdFile
      }
      else {
        if (topFile == originalFile) {
          var createdFile = commandCompletionFactory.createFile(originalFile, adjustedText)
          if (createdFile == null) {
            createdFile = PsiFileFactory.getInstance(topEditor.project)
              .createFileFromText(topFile.getName(), topFile.getLanguage(), adjustedText, true, true, false, topFile.virtualFile)
            if (createdFile is PsiFileImpl) {
              createdFile.setOriginalFile(topFile)
            }
          }
          createdFile
        }
        else {
          val createdFile = PsiFileFactory.getInstance(topEditor.project)
            .createFileFromText(topFile.getName(), topFile.getLanguage(), adjustedText, true, true, false, topFile.virtualFile)
          if (createdFile is PsiFileImpl) {
            createdFile.setOriginalFile(topFile)
          }
          createdFile
        }
      }

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
}

@ApiStatus.Internal
internal class CommandCompletionUnsupportedOperationException
  : UnsupportedOperationException("It's unexpected to invoke this method on a command completion calculating.")

internal class MyEditor(psiFileCopy: PsiFile, private val settings: EditorSettings) : ImaginaryEditor(psiFileCopy.project,
                                                                                                     psiFileCopy.viewProvider.document!!) {
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
  while (indexOf > 0 && offset - indexOf >= 0 && text.substring(offset - indexOf, offset) != suffix.substring(0, indexOf)) {
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
    if (currentIndex <= 1 || text[offset - currentIndex] != '\n') return 0
    while (currentIndex >= 0 && text[offset - currentIndex].isWhitespace()) {
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
  if (indexOf == 1) {
    //one point
    return InvocationCommandType.PartialSuffix(text.substring(offset - indexOf + 1, offset),
                                               text.substring(offset - indexOf, offset - indexOf + 1))
  }
  //two points
  else if (offset - indexOf + 2 <= text.length && text.substring(offset - indexOf, offset - indexOf + 2) == suffix) {
    return InvocationCommandType.FullSuffix(text.substring(offset - indexOf + 2, offset),
                                            text.substring(offset - indexOf, offset - indexOf + 2))
  }
  if (indexOf > 0 && text.substring(offset - indexOf, offset - indexOf + 2).contains(factory.suffix())) {
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
