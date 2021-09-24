// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.PropertyKey

abstract class AbstractEditIntentionShortcutAction(protected val intention: IntentionAction,
                                                   @PropertyKey(resourceBundle = CodeInsightBundle.BUNDLE) private val textKey: String)
  : IntentionAction, LowPriorityAction {

  override fun getFamilyName(): String = CodeInsightBundle.message(textKey)

  override fun getText(): String = familyName
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
  override fun startInWriteAction(): Boolean = false
}

/** Action shown in each intention's submenu to allow assigning a keyboard shortcut for it */
class AssignShortcutToIntentionAction(intention: IntentionAction)
  : AbstractEditIntentionShortcutAction(intention, "assign.intention.shortcut") {

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    IntentionShortcutManager.getInstance().promptForIntentionShortcut(intention, project)
  }
}

/** Action shown in each intention's submenu to allow modifying its shortcut */
class EditShortcutToIntentionAction(intention: IntentionAction)
  : AbstractEditIntentionShortcutAction(intention, "edit.intention.shortcut") {

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    IntentionShortcutManager.getInstance().promptForIntentionShortcut(intention, project)
  }
}

/** Action shown in each intention's submenu to remove its shortcut */
class RemoveIntentionActionShortcut(intention: IntentionAction)
  : AbstractEditIntentionShortcutAction(intention, "remove.intention.shortcut") {

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    IntentionShortcutManager.getInstance().removeFirstIntentionShortcut(intention)
  }
}
