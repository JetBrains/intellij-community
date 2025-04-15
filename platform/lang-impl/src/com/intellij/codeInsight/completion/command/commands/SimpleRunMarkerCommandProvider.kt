// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.getDataContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.codeInsight.daemon.impl.GutterIntentionAction
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.execution.lineMarker.LineMarkerActionWrapper
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.execution.lineMarker.RunLineMarkerProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.JBIterable.from
import org.jetbrains.annotations.Nls
import java.util.function.Predicate
import javax.swing.Icon

// disabled for experiment
/**
 * A provider for run marker-related completion commands in the code editor.
 */
internal class SimpleRunMarkerCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    return runBlockingCancellable {
      val offset = context.offset
      val psiFile = context.psiFile
      val project = context.project
      val editor = context.editor
      if (offset > 0) {
        val c = psiFile.fileDocument.immutableCharSequence[offset - 1]
        if (StringUtil.isJavaIdentifierPart(c) || c in "])}>") {
          return@runBlockingCancellable emptyList()
        }
      }
      val (currentElement, collectedActions) = collectActions(psiFile, offset, project)
      @Suppress("SENSELESS_COMPARISON")
      if (currentElement == null) return@runBlockingCancellable emptyList()
      val filter = Predicate { action: IntentionAction? ->
        IntentionActionFilter.EXTENSION_POINT_NAME.extensionList.all { f: IntentionActionFilter? ->
          f != null && action != null && f.accept(action, psiFile, offset)
        }
      }
      val commands = mutableListOf<CompletionCommand>()
      val myGuttersRaw: List<AnAction> = collectedActions
      val dumbService = DumbService.getInstance(project)
      val group = DefaultActionGroup(ArrayList<AnAction?>(LinkedHashSet<AnAction?>(myGuttersRaw)))
      val context = getTargetContext(offset, editor)
      val dataContext = getDataContext(psiFile, editor, context)
      val actionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
      actionEvent.updateSession = UpdateSession.EMPTY
      Utils.initUpdateSession(actionEvent)

      val session = actionEvent.updateSession
      val activeActions = from(session.expandedChildren(group))
        .filter { dumbService.isUsableInCurrentContext(it) }
        .filter { o: AnAction? -> o !is Separator && o != null && session.presentation(o).isEnabledAndVisible }

      for (action in activeActions) {
        try {
          if (action !is LineMarkerActionWrapper) continue
          val delegate = action.delegate
          val presentation: Presentation = action.templatePresentation.clone()
          val runLineMarkerContributorInfo = Info(delegate)
          val text = runLineMarkerContributorInfo.tooltipProvider.apply(currentElement)
          if (text?.isEmpty() == true) continue
          val intentionAction = GutterIntentionAction(action, 0, false)
          intentionAction.updateFromPresentation(presentation)
          if (!filter.test(intentionAction)) continue
          val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(intentionAction.action)
          commands.add(RunMarkerCompletionCommand(currentElement.textRange.startOffset, text, intentionAction.getIcon(Iconable.ICON_FLAG_VISIBILITY), shortcutText))
        }
        catch (_: Exception) {

        }
      }

      return@runBlockingCancellable commands
    }
  }
}

private fun collectActions(
  psiFile: PsiFile,
  offset: Int,
  project: Project,
): Pair<PsiElement?, MutableList<AnAction>> {
  val runLineMarkerProvider = RunLineMarkerProvider()
  var currentElement = psiFile.findElementAt(offset)
  val dumbService = DumbService.getInstance(project)
  val collectedActions = mutableListOf<AnAction>()
  val fileDocument = psiFile.fileDocument
  val lineNumber = fileDocument.getLineNumber(offset)
  while (currentElement != null) {
    for (child in currentElement.children) {
      val startOffset = child.textRange?.startOffset ?: continue
      if (fileDocument.getLineNumber(startOffset) != lineNumber) continue
      val lineMarkerInfo = runLineMarkerProvider.getLineMarkerInfo(child)
      if (lineMarkerInfo is MergeableLineMarkerInfo) {
        val r = lineMarkerInfo.createGutterRenderer()
        val group: ActionGroup? = r.popupMenuActions
        if (group == null) continue
        val children = if (group is DefaultActionGroup) group.getChildren(ActionManager.getInstance()) else group.getChildren(null)
        for (action in children) {
          if (!dumbService.isUsableInCurrentContext(action)) continue
          collectedActions.add(action)
        }
      }
    }
    if (collectedActions.isNotEmpty()) break
    currentElement = currentElement.parent
  }
  return Pair(currentElement, collectedActions)
}

private class RunMarkerCompletionCommand(
  private val offsetElement: Int,
  @NlsSafe override val name: String,
  override val icon: Icon?,
  override val additionalInfo: String?,
) : CompletionCommand(), PossiblyDumbAware {
  override val i18nName: @Nls String
    get() = ""

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val (_, collectedActions) = runWithModalProgressBlocking(psiFile.project, AnalysisBundle.message("scanning.scope.progress.title")) {
      collectActions(psiFile, offsetElement, psiFile.project)
    }
    val cachedIntentions = CachedIntentions(psiFile.project, psiFile, editor)
    val intentionsInfo = ShowIntentionsPass.IntentionsInfo()
    intentionsInfo.guttersToShow.addAll(collectedActions)
    cachedIntentions.wrapAndUpdateActions(intentionsInfo, true)
    cachedIntentions.wrapAndUpdateGutters()
    var intentionAction: IntentionAction? = null
    for (caching in cachedIntentions.gutters) {
      if (caching.text == name) {
        intentionAction = caching.action
        break
      }
    }
    if (intentionAction == null) return
    if (editor == null) return
    if (ShowIntentionActionsHandler.availableFor(psiFile, editor, offset, intentionAction)) {
      ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intentionAction, name)
    }
  }
}
