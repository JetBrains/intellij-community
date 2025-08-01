// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.codeInsight.completion.command.*
import com.intellij.ide.DataManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.Nls

/**
 * Abstract base class that provides a framework for generating completion commands
 * in a code completion system.
 */
abstract class AbstractGenerateCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val psiFile = context.psiFile
    val project = context.project
    val offset = context.offset
    val editor = context.editor
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return emptyList()
    val element = psiFile.findElementAt(if (offset - 1 >= 0) offset - 1 else offset)
    if (element == null) return emptyList()
    if (!generationIsAvailable(element, offset)) return emptyList()
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val actionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.Companion.NONE, null)
    actionEvent.updateSession = UpdateSession.EMPTY
    Utils.initUpdateSession(actionEvent)
    val generateActions: MutableList<CodeInsightAction> = ArrayList()
    val session = actionEvent.updateSession
    val dumbService = DumbService.Companion.getInstance(project)
    val activeActions = JBIterable.from(
      session.expandedChildren(ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE) as ActionGroup))
      .filter { dumbService.isUsableInCurrentContext(it) }
      .filter { o: AnAction? -> o !is Separator && o != null && session.presentation(o).isEnabledAndVisible }
    for (action in activeActions) {
      if (action is CodeInsightAction) {
        generateActions.add(action)
      }
    }
    return generateActions.map { GenerateCompletionCommand(it) }
  }

  protected fun isOnlySpaceInLine(document: Document, offset: Int): Boolean {
    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val immutableCharSequence = document.immutableCharSequence
    for (i in lineStartOffset..offset) {
      if (i >= immutableCharSequence.length) break
      if (!immutableCharSequence[i].isWhitespace()) {
        return false
      }
    }
    return true
  }

  /**
   * Determines if generation actions are available for the provided PSI element.
   *
   * @param element A PSI element for which the availability of generation actions is to be checked.
   * @param offset A current offset.
   * @return `true` if generation actions are available for the specified element; `false` otherwise.
   */
  abstract fun generationIsAvailable(element: PsiElement, offset: Int): Boolean

  protected class GenerateCompletionCommand(
    val action: CodeInsightAction,
    var customName: String? = null,
    var customI18nName: @Nls String? = null,
  ) : CompletionCommand() {

    override val additionalInfo: String?
      get() {
        val shortcutText = KeymapUtil.getFirstKeyboardShortcutText("Generate")
        if (shortcutText.isNotEmpty()) {
          return shortcutText
        }
        return null
      }

    override val presentableName: @Nls String
      get() = customI18nName ?: (CodeInsightBundle.message("command.completion.generate.text", action.templateText))

    override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
      if (editor == null) return
      val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
      val presentation: Presentation = action.templatePresentation.clone()
      val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
      ActionUtil.performAction(action, event)
    }
  }
}