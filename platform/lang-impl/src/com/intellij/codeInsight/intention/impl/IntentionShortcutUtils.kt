// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("IntentionShortcutUtils")

package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

fun IntentionAction.invokeAsAction(editor: Editor?, file: PsiFile) {
  ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, this, CodeInsightBundle.message("intention.action.wrapper.name", familyName))
}

/** A unique identifier for an intention action starting with [WRAPPER_PREFIX] */
internal val IntentionAction.wrappedActionId: String get() =
  WRAPPER_PREFIX +
  if (this is IntentionActionDelegate) implementationClassName else javaClass.name
