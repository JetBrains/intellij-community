/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentFromVoidWithStatementIntentionAction extends PsiUpdateModCommandAction<PsiElement> {
  private final @NotNull SmartPsiElementPointer<PsiExpression> myLExpr;

  public ReplaceAssignmentFromVoidWithStatementIntentionAction(@NotNull PsiElement parent, @NotNull PsiExpression lExpr) {
    super(parent);
    myLExpr = SmartPointerManager.createPointer(lExpr);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("remove.left.side.of.assignment");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return myLExpr.getElement() != null ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression lExpr = myLExpr.getElement();
    if (lExpr != null) {
      element.replace(lExpr);
    }
  }
}
