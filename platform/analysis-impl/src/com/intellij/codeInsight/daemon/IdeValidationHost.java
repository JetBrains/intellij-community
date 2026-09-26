// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


public interface IdeValidationHost extends Validator.ValidationHost {
  void addMessageWithFixes(PsiElement context, @InspectionMessage String message,
                           @NotNull ErrorType type, IntentionAction @NotNull ... fixes);
}
