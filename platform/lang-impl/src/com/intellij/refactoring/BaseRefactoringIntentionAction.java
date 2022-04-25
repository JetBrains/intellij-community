// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

public abstract class BaseRefactoringIntentionAction extends PsiElementBaseIntentionAction implements Iconable, HighPriorityAction {
  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }
}
