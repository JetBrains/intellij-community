// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IntentionShortcutUtils")

package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

@JvmOverloads
fun IntentionAction.invokeAsAction(editor: Editor, file: PsiFile, intentionSource: IntentionSource = IntentionSource.OTHER) {
  ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, this,
                                                    CodeInsightBundle.message("intention.action.wrapper.name", familyName),
                                                    intentionSource)
}

/** A unique identifier for an intention action starting with [WRAPPER_PREFIX] */
internal val IntentionAction.wrappedActionId: String
  get() = WRAPPER_PREFIX +
          if (this is IntentionActionDelegate) implementationClassName else javaClass.name
