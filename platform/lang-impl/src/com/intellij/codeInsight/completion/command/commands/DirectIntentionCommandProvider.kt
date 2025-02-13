// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.codeInsight.intention.*
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException
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
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.ProjectTypeService
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager.Companion.getInstance
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import java.util.function.Predicate

/**
 * Provides supplying completion commands
 * based on intentions, errors, and inspections within the given context.
 */
internal class DirectIntentionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!Registry.`is`("ide.completion.command.enabled")) return emptyList()
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

      val errorCache = originalEditor.getUserData(ERROR_CACHE)
      val cachedErrorCommand = errorCache?.getCommands(psiFile.fileDocument, offset)
      cachedErrorCommand?.let {
        result.addAll(it)
      }
      val asyncErrorHighlighting = if (cachedErrorCommand == null) asyncErrorHighlighting(psiFile, editor, offset) else null

      val inspectionCache = originalEditor.getUserData(INSPECTION_CACHE)
      val cachedInspectionCommand = inspectionCache?.getCommands(psiFile.fileDocument, offset)
      cachedInspectionCommand?.let {
        result.addAll(it)
      }
      val asyncInspections = if (cachedInspectionCommand == null) asyncInspectionHighlighting(psiFile, editor, offset) else null

      val asyncIntentions = asyncIntentions(editor, psiFile, originalPsiFile, offset)

      val errors = asyncErrorHighlighting?.await()
      errors?.let {
        result.addAll(errors)
        originalEditor.putUserData(ERROR_CACHE, IntentionCache(
          commands = errors,
          offset = offset,
          hashCode = psiFile.fileDocument.immutableCharSequence.hashCode()
        ))
      }


      val inspections = asyncInspections?.await()
      inspections?.let {
        result.addAll(inspections)
        originalEditor.putUserData(INSPECTION_CACHE, IntentionCache(
          commands = inspections,
          offset = offset,
          hashCode = psiFile.fileDocument.immutableCharSequence.hashCode()
        ))
      }

      val intentions = asyncIntentions.await()
      result.addAll(intentions)
      return@runBlockingCancellable result
    }
  }

  private val ERROR_CACHE: Key<IntentionCache> = Key.create("completion.command.error.cache")
  private val INSPECTION_CACHE: Key<IntentionCache> = Key.create("completion.command.inspection.cache")

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
    editor: Editor,
    offset: Int,
  ): Deferred<List<CompletionCommand>> = async {
    return@async readAction {
      val intentionCommandSkipper = IntentionCommandSkipper.EP_NAME.forLanguage(psiFile.language)
      val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
      val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
      val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
      val topLevelOffset = injectedLanguageManager.injectedToHost(psiFile, offset)
      val isInjected = topLevelFile != psiFile

      val profileToUse = getInstance(psiFile.project).getCurrentProfile()
      val inspectionWrapper = InspectionProfileWrapper(profileToUse)
      val inspectionTools = getInspectionTools(inspectionWrapper, psiFile)
      val lineRange = getLineRange(topLevelFile, topLevelOffset)
      val indicator = EmptyProgressIndicator()

      val inspectionResult = jobToIndicator(coroutineContext.job, indicator) {
        if (!isInjected) {
          InspectionEngine.inspectEx(inspectionTools, topLevelFile, lineRange, lineRange, true, false, true, indicator, fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
            return true
          })
        }
        else {
          val textRange = getLineRange(psiFile, offset)
          InspectionEngine.inspectElements(inspectionTools, psiFile, textRange, true, true, indicator,
                                           PsiTreeUtil.collectElements(psiFile) { it.textRange.intersects(textRange) }.toList(), fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
            return true
          })
        }
      }
      val result: MutableList<CompletionCommand> = mutableListOf()
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
          for (i in 0..fixes.size - 1) {
            val action = QuickFixWrapper.wrap(descriptor, i)
            if (action is EmptyIntentionAction) continue
            if (intentionCommandSkipper != null && intentionCommandSkipper.skip(action, psiFile, offset)) continue
            if (!isInjected && !ShowIntentionActionsHandler.availableFor(topLevelFile, topLevelEditor, topLevelOffset, action)) continue
            if (isInjected && !ShowIntentionActionsHandler.availableFor(psiFile, editor, offset, action)) continue
            val priority = if (level.getSeverity(null) == INFORMATION) 70 else 80
            val icon = if (level.getSeverity(null) == INFORMATION) AllIcons.Actions.IntentionBulbGrey else AllIcons.Actions.IntentionBulb

            result.add(DirectInspectionFixCompletionCommand(
              inspectionId = toolId,
              name = action.text,
              priority = priority,
              icon = icon,
              highlightInfo = HighlightInfoLookup(textRange, level.attributesKey, priority)))
          }
        }
      }
      return@readAction result
    }
  }

  private fun CoroutineScope.asyncErrorHighlighting(psiFile: PsiFile, editor: Editor, offset: Int): Deferred<List<CompletionCommand>> {
    return async {
      val result: MutableList<CompletionCommand> = mutableListOf()
      readAction {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
        val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
        val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
        val topLevelOffset = injectedLanguageManager.injectedToHost(psiFile, offset)
        val isInjected = topLevelFile != psiFile
        val indicator = DaemonProgressIndicator()
        val errorHighlightings: List<HighlightInfo?>? = jobToIndicator(coroutineContext.job, indicator) {
          HighlightVisitorBasedInspection.runAnnotatorsInGeneralHighlighting(topLevelFile, true, true, true)
        }
        if (errorHighlightings == null) return@readAction
        var insideRange = getLineRange(topLevelFile, topLevelOffset)
        if (isInjected) {
          insideRange = insideRange.intersection(
            TextRange(injectedLanguageManager.injectedToHost(psiFile, 0),
                      injectedLanguageManager.injectedToHost(psiFile, psiFile.fileDocument.textLength)))
        }
        for (info: HighlightInfo? in errorHighlightings) {
          if (info == null) continue
          if (!insideRange.intersects(info.startOffset, info.endOffset) ||
              (isInjected && !insideRange.contains(TextRange(info.startOffset, info.endOffset)))) continue
          val fixes: MutableList<IntentionActionDescriptor> = ArrayList<IntentionActionDescriptor>()
          ShowIntentionsPass.addAvailableFixesForGroups(info, topLevelEditor, topLevelFile, fixes, -1, offset, false)
          for (descriptor in fixes) {
            if (descriptor.action is EmptyIntentionAction) continue
            val command = DirectErrorFixCompletionCommand(name = descriptor.action.text,
                                                          priority = 100,
                                                          icon = AllIcons.Actions.QuickfixBulb,
                                                          highlightInfo = HighlightInfoLookup(TextRange(info.startOffset, info.endOffset),
                                                                                              CodeInsightColors.ERRORS_ATTRIBUTES, 100))
            result.add(command)
          }
        }
      }
      return@async result
    }
  }

  private fun CoroutineScope.asyncIntentions(
    editor: Editor,
    psiFile: PsiFile,
    originalFile: PsiFile,
    offset: Int,
  ): Deferred<List<CompletionCommand>> = async {
    return@async readAction {
      val intentionCommandSkipper = IntentionCommandSkipper.EP_NAME.forLanguage(psiFile.language)
      val availableIntentions = IntentionManager.getInstance().getAvailableIntentions(mutableListOf(originalFile.language.id))
      val actionsToShow = IntentionsInfo()
      for (action in availableIntentions) {
        val intentionAction = IntentionActionDelegate.unwrap(action)
        val descriptor =
          IntentionActionDescriptor(action, null, null,
                                    if (intentionAction is Iconable)
                                      intentionAction.getIcon(ICON_FLAG_VISIBILITY)
                                    else null, null, null, null, null)
        actionsToShow.intentionsToShow.add(descriptor)
      }
      val dumbService = DumbService.getInstance(originalFile.project)
      val intentionsCache = CachedIntentions(originalFile.project, psiFile, editor)
      val toRemove = mutableListOf<IntentionActionDescriptor>()
      val filter = Predicate { action: IntentionAction? ->
        IntentionActionFilter.EXTENSION_POINT_NAME.extensionList.all { f: IntentionActionFilter -> action != null && f.accept(action, psiFile, editor.caretModel.offset) }
      }

      for (intention in actionsToShow.intentionsToShow) {
        try {
          ProgressManager.checkCanceled()
          if (!dumbService.isUsableInCurrentContext(intention) ||
              !intention.action.isAvailable(originalFile.project, editor, psiFile) &&
              //todo temporary workaround for AI
              intention.action.familyName !in ("AI Actions…") ||
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
      val result = mutableListOf<CompletionCommand>()
      for (intention in intentionsCache.intentions) {
        if (intention.action is EmptyIntentionAction ||
            intentionCommandSkipper != null && intentionCommandSkipper.skip(intention.action, psiFile, offset)) continue
        val intentionCommand =
          IntentionCompletionCommand(intention, 50, intention.icon ?: AllIcons.Actions.IntentionBulbGrey, null)
        result.add(intentionCommand)
      }
      return@readAction result
    }
  }
}

internal fun getLineRange(psiFile: PsiFile, offset: Int): TextRange {
  val document = psiFile.fileDocument
  val lineNumber = document.getLineNumber(offset)
  val lineStartOffset = document.getLineStartOffset(lineNumber)
  val insideRange = TextRange(lineStartOffset, offset)
  return insideRange
}

private fun getInspectionTools(profile: InspectionProfileWrapper, file: PsiFile): MutableList<LocalInspectionToolWrapper?> {
  val toolWrappers = profile.inspectionProfile.getInspectionTools(file)
  val enabled: MutableList<LocalInspectionToolWrapper?> = ArrayList<LocalInspectionToolWrapper?>()
  val projectTypes = ProjectTypeService.getProjectTypeIds(file.project)

  for (toolWrapper in toolWrappers) {
    ProgressManager.checkCanceled()
    if (!toolWrapper.isApplicable(projectTypes)) continue
    val key = toolWrapper.getDisplayKey()
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

interface IntentionCommandSkipper {
  companion object {
    internal val EP_NAME: LanguageExtension<IntentionCommandSkipper> = LanguageExtension<IntentionCommandSkipper>("com.intellij.codeInsight.completion.intention.skipper")
  }

  fun skip(action: CommonIntentionAction, psiFile: PsiFile, offset: Int): Boolean = false
}

