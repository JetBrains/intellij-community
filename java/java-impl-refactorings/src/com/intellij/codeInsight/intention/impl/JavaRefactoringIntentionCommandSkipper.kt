// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.completion.command.commands.IntentionCommandSkipper
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class JavaRefactoringIntentionCommandSkipper : IntentionCommandSkipper {
  override fun skip(action: CommonIntentionAction, psiFile: PsiFile, offset: Int): Boolean {
    if (action !is IntentionAction) return false
    val unwrappedIntentionAction = IntentionActionDelegate.unwrap(action)
    return unwrappedIntentionAction is IntroduceVariableIntentionAction
  }
}