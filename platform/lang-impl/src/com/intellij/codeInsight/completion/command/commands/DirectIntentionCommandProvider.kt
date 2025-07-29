// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.codeInsight.intention.*
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComputable
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.HighlightSeverity.INFORMATION
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.ProjectTypeService
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager.Companion.getInstance
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import java.lang.ref.SoftReference
import java.util.concurrent.CancellationException
import java.util.function.Predicate

/**
 * Provides supplying completion commands
 * based on intentions, errors, and inspections within the given context.
 */
internal class DirectIntentionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return emptyList()
    val originalEditor = context.originalEditor
    val psiFile = context.psiFile
    val offset = context.offset
    val editor = context.editor
    val originalPsiFile = context.originalPsiFile

    //doesn't work for empty line
    val fileDocument = psiFile.fileDocument
    val lineNumber = fileDocument.getLineNumber(offset)
    val lineStartOffset = fileDocument.getLineStartOffset(lineNumber)
    val lineEndOffset = fileDocument.getLineEndOffset(lineNumber)
    val subSequence = fileDocument.charsSequence.subSequence(lineStartOffset, lineEndOffset)
    if (subSequence.isBlank()) {
      return emptyList()
    }

    return runBlockingCancellable {
      val result: MutableList<CompletionCommand> = ArrayList()

      val errorCache = originalEditor.getUserData(ERROR_CACHE)?.get()
      val cachedErrorCommand = errorCache?.getCommands(psiFile.fileDocument, offset)
      cachedErrorCommand?.let {
        result.addAll(it)
      }
      val asyncErrorHighlighting = if (cachedErrorCommand == null) asyncErrorHighlighting(psiFile, editor, offset) else null

      val inspectionCache = originalEditor.getUserData(INSPECTION_CACHE)?.get()
      val cachedInspectionCommand = inspectionCache?.getCommands(psiFile.fileDocument, offset)
      cachedInspectionCommand?.let {
        result.addAll(it)
      }
      val inspectionEditor = createCustomEditor(psiFile, editor, offset)
      //can move the cursor!
      val asyncInspections = if (cachedInspectionCommand == null) asyncInspectionHighlighting(psiFile, originalPsiFile, inspectionEditor, offset) else null

      val intentionEditor = createCustomEditor(psiFile, editor, offset)
      //can move the cursor!
      val asyncIntentions = asyncIntentions(intentionEditor, psiFile, originalPsiFile, offset)

      val errors = asyncErrorHighlighting?.await()
      errors?.let {
        result.addAll(errors)
        originalEditor.putUserData(ERROR_CACHE, SoftReference(IntentionCache(
          commands = errors,
          offset = offset,
          hashCode = psiFile.fileDocument.immutableCharSequence.hashCode()
        )))
      }


      val inspections = asyncInspections?.await()
      inspections?.let {
        result.addAll(inspections)
        originalEditor.putUserData(INSPECTION_CACHE, SoftReference(IntentionCache(
          commands = inspections,
          offset = offset,
          hashCode = psiFile.fileDocument.immutableCharSequence.hashCode()
        )))
      }

      val intentions = asyncIntentions.await()
      result.addAll(intentions)
      return@runBlockingCancellable result
    }
  }

  private fun createCustomEditor(
    psiFile: PsiFile,
    editor: Editor,
    offset: Int,
  ): MyEditor {
    val intentionEditor = MyEditor(psiFile, editor.settings)
    intentionEditor.caretModel.moveToOffset(offset)
    return intentionEditor
  }

  private val ERROR_CACHE: Key<SoftReference<IntentionCache>> = Key.create("completion.command.error.cache")
  private val INSPECTION_CACHE: Key<SoftReference<IntentionCache>> = Key.create("completion.command.inspection.cache")

  private data class IntentionCache(
    private val commands: List<CompletionCommand>,
    private val offset: Int,
    private val hashCode: Int,
  ) {
    fun getCommands(document: Document, offset: Int): List<CompletionCommand>? {
      if (document.immutableCharSequence.hashCode() == hashCode && offset == this.offset) return commands
      return null
    }
  }

  private fun CoroutineScope.asyncInspectionHighlighting(
    psiFile: PsiFile,
    originalFile: PsiFile,
    editor: Editor,
    offset: Int,
  ): Deferred<List<CompletionCommand>> = async {
    return@async readAction {
      var language = psiFile.language
      val injectedElementAt = InjectedLanguageManager.getInstance(psiFile.project).findInjectedElementAt(psiFile, offset)
      if (injectedElementAt != null) {
        language = injectedElementAt.language
      }
      val offsetProvider = IntentionCommandOffsetProvider.EP_NAME.forLanguage(language)

      val result: MutableMap<String, CompletionCommandWithErrorLevel> = mutableMapOf()
      try {
        val intentionCommandSkipper = IntentionCommandSkipper.EP_NAME.forLanguage(psiFile.language)
        val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
        val allOffsets = offsetProvider?.findOffsets(psiFile, offset) ?: mutableListOf(offset)
        val fileDocument = psiFile.fileDocument
        val lineByOffset = mutableMapOf<Int, Int>()
        for (offset in allOffsets) {
          val lineNumber = fileDocument.getLineNumber(offset)
          lineByOffset.compute(lineNumber) { _, v -> if (v == null) offset else maxOf(v, offset) }
        }
        for (currentOffset in lineByOffset.values) {
          editor.caretModel.moveToOffset(currentOffset)
          val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
          val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
          val topLevelOffset = injectedLanguageManager.injectedToHost(psiFile, currentOffset)
          val isInjected = topLevelFile != psiFile
          val profileToUse = getInstance(psiFile.project).getCurrentProfile()
          val inspectionWrapper = InspectionProfileWrapper(profileToUse)
          val inspectionTools = getInspectionTools(inspectionWrapper, originalFile)
          val lineRange = getLineRange(topLevelFile, topLevelOffset)
          val indicator = EmptyProgressIndicator()
          val inspectionResult = jobToIndicator(coroutineContext.job, indicator) {
            if (!isInjected) {
              InspectionEngine.inspectEx(inspectionTools, topLevelFile, lineRange, lineRange, true, false, true, indicator, fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
                return true
              })
            }
            else {
              val textRange = getLineRange(psiFile, currentOffset)
              InspectionEngine.inspectElements(inspectionTools, psiFile, textRange, true, true, indicator,
                                               PsiTreeUtil.collectElements(psiFile) { it.textRange.intersects(textRange) }.toList(), fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
                return true
              })
            }
          }
          for (entry: MutableMap.MutableEntry<LocalInspectionToolWrapper?, List<ProblemDescriptor?>?> in inspectionResult.entries) {
            val inspectionToolWrapper: LocalInspectionToolWrapper = entry.key ?: continue
            val toolId = inspectionToolWrapper.shortName
            val descriptors: List<ProblemDescriptor?> = entry.value ?: continue
            for (descriptor in descriptors) {
              if (descriptor == null) continue
              val fixes = descriptor.fixes ?: continue
              if (descriptor !is ProblemDescriptorBase) continue
              var textRange = descriptor.textRange ?: continue
              if (!lineRange.intersects(textRange)) continue
              if (isInjected) {
                textRange = injectedLanguageManager.injectedToHost(psiFile, textRange)
              }
              val displayKey: HighlightDisplayKey = inspectionToolWrapper.getDisplayKey() ?: continue
              val severity: HighlightSeverity = inspectionWrapper.getErrorLevel(displayKey, topLevelFile).severity
              val severityRegistrar: SeverityRegistrar = inspectionWrapper.profileManager.severityRegistrar
              val level = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, severity, severityRegistrar)
              //necessary to be compatible with call site
              editor.caretModel.moveToOffset(offset)
              topLevelEditor.caretModel.moveToOffset(offset)
              for (i in 0..<fixes.size) {
                val action = QuickFixWrapper.wrap(descriptor, i)
                if (action is EmptyIntentionAction) continue
                if (intentionCommandSkipper != null && intentionCommandSkipper.skip(action, psiFile, currentOffset)) continue
                if (!isInjected && !ShowIntentionActionsHandler.availableFor(topLevelFile, topLevelEditor, topLevelOffset, action)) continue
                if (isInjected && !ShowIntentionActionsHandler.availableFor(psiFile, editor, currentOffset, action)) continue
                val isInfo = level.getSeverity(null) == INFORMATION
                val priority = if (isInfo) 70 else 80
                val icon = if (isInfo) AllIcons.Actions.IntentionBulbGrey else AllIcons.Actions.IntentionBulb

                result[toolId + ":" + action.text] = CompletionCommandWithErrorLevel(DirectInspectionFixCompletionCommand(
                  inspectionId = toolId,
                  presentableName = action.text,
                  priority = priority,
                  icon = icon,
                  highlightInfo = HighlightInfoLookup(textRange, level.attributesKey, priority),
                  targetOffset = currentOffset,
                  previewProvider = {
                    computePreview(psiFile, action, editor, offset)
                  }), if (isInfo) ErrorLevel.INFO else ErrorLevel.WARNING)
              }
            }
          }
        }
      }
      catch (e: Exception) {
        if (e is ControlFlowException || e is CancellationException) {
          throw e
        }
        thisLogger().error("Can't collect inspections", e)
      }

      var strictRange = false
      for (commandWithLevel in result.values) {
        if (commandWithLevel.level == ErrorLevel.WARNING && commandWithLevel.command.highlightInfo?.range?.endOffset == offset) {
          strictRange = true
          break
        }
      }

      if (strictRange) {
        return@readAction result.values.map { it.command }.filter { it.highlightInfo?.range?.contains(offset - 1) == true }.toList()
      }

      return@readAction result.values.map { it.command }.toList()
    }
  }

  private fun computePreview(
    psiFile: PsiFile,
    action: IntentionAction,
    editor: Editor,
    offset: Int,
  ): IntentionPreviewInfo? = try {
    IntentionPreviewComputable(psiFile.project, action, psiFile, editor, offset).call()
  }
  catch (e: Exception) {
    if (e is ControlFlowException || e is CancellationException) {
      throw e
    }
    thisLogger().error("Can't create preview", e)
    null
  }

  private fun CoroutineScope.asyncErrorHighlighting(psiFile: PsiFile, editor: Editor, offset: Int): Deferred<List<CompletionCommand>> {
    return async {
      val result: MutableList<CompletionCommand> = mutableListOf()
      readAction {
        try {
          val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
          val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
          val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
          val topLevelOffset = injectedLanguageManager.injectedToHost(psiFile, offset)

          val fileDocument = topLevelFile.fileDocument
          val lineNumber = fileDocument.getLineNumber(topLevelOffset)
          val startLineNumber = maxOf(0, lineNumber - 2)
          val endLineNumber = minOf(fileDocument.lineCount - 1, lineNumber + 2)
          val startOffset = fileDocument.getLineStartOffset(startLineNumber)
          val endOffset = fileDocument.getLineEndOffset(endLineNumber)
          val isInjected = topLevelFile != psiFile
          val indicator = DaemonProgressIndicator()
          val errorHighlightings: List<HighlightInfo?>? = jobToIndicator(coroutineContext.job, indicator) {
            ApplicationManager.getApplication().runReadAction<List<HighlightInfo?>?> {
              HighlightVisitorBasedInspection.runAnnotatorsInGeneralHighlighting(topLevelFile,
                                                                                 startOffset,
                                                                                 endOffset,
                                                                                 ProperTextRange(startOffset, endOffset),
                                                                                 true, true, true)
            }
          }
          if (errorHighlightings == null) return@readAction
          var insideRange = getLineRange(topLevelFile, topLevelOffset)
          if (isInjected) {
            insideRange = insideRange.intersection(
              TextRange(injectedLanguageManager.injectedToHost(psiFile, 0),
                        injectedLanguageManager.injectedToHost(psiFile, psiFile.fileDocument.textLength)))
          }
          val lazyQuickFixUpdater = LazyQuickFixUpdater.getInstance(psiFile.project)
          for (info: HighlightInfo? in errorHighlightings) {
            if (info == null) continue
            if (!insideRange.intersects(info.startOffset, info.endOffset) ||
                (isInjected && !insideRange.contains(TextRange(info.startOffset, info.endOffset)))) continue
            if (info.hasLazyQuickFixes()) {
              info.updateLazyFixesPsiTimeStamp(PsiModificationTracker.getInstance(psiFile.project).modificationCount)
              lazyQuickFixUpdater.waitQuickFixesSynchronously(psiFile, editor, info)
            }
            val fixes: MutableList<IntentionActionDescriptor> = ArrayList()
            ShowIntentionsPass.addAvailableFixesForGroups(info, topLevelEditor, topLevelFile, fixes, -1, offset, false)
            for (descriptor in fixes) {
              if (descriptor.action is EmptyIntentionAction) continue
              var name = descriptor.action.text
              val prefix = "<html>"
              val suffix = "</html>"
              if (name.startsWith(prefix) && name.endsWith(suffix)) {
                @Suppress("HardCodedStringLiteral")
                name = name.substring(prefix.length, name.length - suffix.length)
              }
              val command = DirectErrorFixCompletionCommand(presentableName = name,
                                                            priority = 100,
                                                            icon = AllIcons.Actions.QuickfixBulb,
                                                            highlightInfo = HighlightInfoLookup(TextRange(info.startOffset, info.endOffset),
                                                                                                CodeInsightColors.ERRORS_ATTRIBUTES, 100),
                                                            previewProvider = {
                                                              computePreview(psiFile, descriptor.action, editor, offset)
                                                            })
              result.add(command)
            }
          }
          result.addAll(errorFixesWithHighlighting(psiFile, errorHighlightings, offset))
        }
        catch (e: Exception) {
          if (e is ControlFlowException || e is CancellationException) {
            throw e
          }
          thisLogger().error("Can't collect errors", e)
        }
      }
      return@async result
    }
  }

  private fun errorFixesWithHighlighting(psiFile: PsiFile, errorHighlightings: List<HighlightInfo?>, offset: Int): Collection<CompletionCommand> {
    val project = psiFile.project
    val dumbService = DumbService.getInstance(project)
    val result = mutableListOf<CompletionCommand>()
    val highlightInfos = errorHighlightings.filterNotNull()
    for (provider in dumbService.filterByDumbAwareness(ErrorFixCommandProvider.EP_NAME.extensionList)) {
      result.addAll(provider.getCommands(psiFile, highlightInfos, offset))
    }
    return result
  }

  private fun CoroutineScope.asyncIntentions(
    editor: Editor,
    psiFile: PsiFile,
    originalFile: PsiFile,
    offset: Int,
  ): Deferred<List<CompletionCommand>> = async {
    return@async readAction {
      val result = mutableMapOf<String, CompletionCommand>()
      try {
        var language = originalFile.language
        val injectedElementAt = InjectedLanguageManager.getInstance(psiFile.project).findInjectedElementAt(psiFile, offset)
        if (injectedElementAt != null) {
          language = injectedElementAt.language
        }
        val offsetProvider = IntentionCommandOffsetProvider.EP_NAME.forLanguage(language)
        val intentionCommandSkipper = IntentionCommandSkipper.EP_NAME.forLanguage(language)

        val offsets = offsetProvider?.findOffsets(psiFile, offset) ?: mutableListOf(offset)

        val availableIntentions = IntentionManager.getInstance().getAvailableIntentions(mutableListOf(language.id))
        for (currentOffset in offsets) {
          val actionsToShow = IntentionsInfo()
          for (action in availableIntentions) {
            val intentionAction = IntentionActionDelegate.unwrap(action)
            val descriptor =
              IntentionActionDescriptor(action, null, null,
                                        if (intentionAction is Iconable) {
                                          intentionAction.getIcon(ICON_FLAG_VISIBILITY)
                                        }
                                        else null, null, null, null, null)
            actionsToShow.intentionsToShow.add(descriptor)
          }
          val dumbService = DumbService.getInstance(originalFile.project)
          editor.caretModel.moveToOffset(currentOffset)
          val intentionsCache = CachedIntentions(originalFile.project, psiFile, editor)
          val toRemove = mutableListOf<IntentionActionDescriptor>()
          val filter = Predicate { action: IntentionAction? ->
            IntentionActionFilter.EXTENSION_POINT_NAME.extensionList.all { f: IntentionActionFilter -> action != null && f.accept(action, psiFile, editor.caretModel.offset) }
          }

          for (intention in actionsToShow.intentionsToShow) {
            try {
              ProgressManager.checkCanceled()
              if (!dumbService.isUsableInCurrentContext(intention) ||
                  !intention.action.isAvailable(originalFile.project, editor, psiFile) ||
                  !filter.test(intention.action)) {
                toRemove.add(intention)
              }
            }
            catch (_: UnsupportedOperationException) {
              toRemove.add(intention)
            }
            catch (_: CommandCompletionUnsupportedOperationException) {
              toRemove.add(intention)
            }
            catch (_: IntentionPreviewUnsupportedOperationException) {
              toRemove.add(intention)
            }
          }
          actionsToShow.intentionsToShow.removeAll(toRemove)
          intentionsCache.wrapAndUpdateActions(actionsToShow, false)
          for (intention in intentionsCache.intentions) {
            if (intention.action is EmptyIntentionAction ||
                intentionCommandSkipper != null && intentionCommandSkipper.skip(intention.action, psiFile, currentOffset)) continue
            val intentionCommand =
              IntentionCompletionCommand(intention, 50, AllIcons.Actions.IntentionBulbGrey, calculateIntentionHighlighting(intention, editor, psiFile, offset), currentOffset) {
                editor.caretModel.moveToOffset(currentOffset)
                computePreview(psiFile, intention.action, editor, currentOffset)
              }
            result[intention.text] = intentionCommand
          }
        }
      }
      finally {
        editor.caretModel.moveToOffset(offset)
      }

      return@readAction result.values.toList()
    }
  }

  fun calculateIntentionHighlighting(intention: IntentionActionWithTextCaching, editor: Editor, psiFile: PsiFile, currentOffset: Int): HighlightInfoLookup? {
    val intentionAction = IntentionActionDelegate.unwrap(intention.action)
    if (intentionAction is CustomizableIntentionAction) {
      val rangesToHighlight = intentionAction.getRangesToHighlight(editor, psiFile)
      for (highlight in rangesToHighlight) {
        if (highlight.rangeInFile.contains(currentOffset)) {
          return HighlightInfoLookup(highlight.rangeInFile
                                       .intersection(TextRange(highlight.rangeInFile.startOffset, currentOffset)),
                                     EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
        }
      }
    }
    val modCommandAction = intentionAction.asModCommandAction() ?: return null
    if (modCommandAction is PsiBasedModCommandAction<*>) {
      modCommandAction.getPresentation(ActionContext.from(editor, psiFile))
        ?.rangesToHighlight()
        ?.firstOrNull { highlightRange ->
          highlightRange.highlightingKind() == Presentation.HighlightingKind.APPLICABLE_TO_RANGE  &&
          highlightRange.range.startOffset <= currentOffset
        }
        ?.let {
          return HighlightInfoLookup(it.range().intersection(TextRange(it.range().startOffset, currentOffset)),
                                     EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
        }
    }
    return null
  }
}

private class CompletionCommandWithErrorLevel(val command: CompletionCommand, val level: ErrorLevel)
private enum class ErrorLevel { INFO, WARNING }

internal fun getLineRange(psiFile: PsiFile, offset: Int): TextRange {
  val document = psiFile.fileDocument
  val lineNumber = document.getLineNumber(offset)
  val lineStartOffset = document.getLineStartOffset(lineNumber)
  val insideRange = TextRange(lineStartOffset, offset)
  return insideRange
}

private fun getInspectionTools(profile: InspectionProfileWrapper, file: PsiFile): MutableList<LocalInspectionToolWrapper?> {
  val toolWrappers = profile.inspectionProfile.getInspectionTools(file)
  val enabled: MutableList<LocalInspectionToolWrapper?> = ArrayList()
  val projectTypes = ProjectTypeService.getProjectTypeIds(file.project)

  for (toolWrapper in toolWrappers) {
    ProgressManager.checkCanceled()
    if (!toolWrapper.isApplicable(projectTypes)) continue
    val key = toolWrapper.getDisplayKey() ?: continue
    if (!profile.isToolEnabled(key, file)) continue
    val wrapper: LocalInspectionToolWrapper?
    if (toolWrapper is LocalInspectionToolWrapper) {
      wrapper = toolWrapper
    }
    else {
      wrapper = (toolWrapper as GlobalInspectionToolWrapper).getSharedLocalInspectionToolWrapper()
      if (wrapper == null) continue
    }
    val language = wrapper.language
    if (language != null && Language.findLanguageByID(language) == null) {
      continue  // filter out at least unknown languages
    }

    try {
      if (!wrapper.isApplicable(file.getLanguage())) {
        continue
      }
    }
    catch (_: IndexNotReadyException) {
      continue
    }
    enabled.add(wrapper)
  }
  return enabled
}


/**
 * This class allows language plugins to contribute specialized error fix commands to IntelliJ's completion system.
 * These commands are displayed when errors are detected in the code and provide context-specific fixes.
 *
 */
interface ErrorFixCommandProvider : PossiblyDumbAware {

  fun getCommands(psiFile: PsiFile, errorHighlightings: List<HighlightInfo>, offset: Int): List<CompletionCommand>

  companion object {
    internal val EP_NAME: ExtensionPointName<ErrorFixCommandProvider> = ExtensionPointName.create("com.intellij.codeInsight.completion.error.intention")
  }
}

interface IntentionCommandSkipper {
  companion object {
    internal val EP_NAME: LanguageExtension<IntentionCommandSkipper> = LanguageExtension<IntentionCommandSkipper>("com.intellij.codeInsight.completion.intention.skipper")
  }

  fun skip(action: CommonIntentionAction, psiFile: PsiFile, offset: Int): Boolean = false
}

interface IntentionCommandOffsetProvider {
  companion object {
    internal val EP_NAME: LanguageExtension<IntentionCommandOffsetProvider> = LanguageExtension<IntentionCommandOffsetProvider>("com.intellij.codeInsight.completion.intention.offset.provider")
  }

  fun findOffsets(psiFile: PsiFile, offset: Int): List<Int> = listOf(offset)
}