/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveCatchUpFix implements IntentionAction {
  private final @NotNull PsiCatchSection myCatchSection;
  private final @NotNull PsiCatchSection myMoveBeforeSection;

  public MoveCatchUpFix(@NotNull PsiCatchSection catchSection, @NotNull PsiCatchSection moveBeforeSection) {
    this.myCatchSection = catchSection;
    myMoveBeforeSection = moveBeforeSection;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new MoveCatchUpFix(PsiTreeUtil.findSameElementInCopy(myCatchSection, target),
                              PsiTreeUtil.findSameElementInCopy(myMoveBeforeSection, target));
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("move.catch.up.text",
                                  JavaHighlightUtil.formatType(myCatchSection.getCatchType()),
                                  JavaHighlightUtil.formatType(myMoveBeforeSection.getCatchType()));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.catch.up.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myCatchSection.isValid()
           && BaseIntentionAction.canModify(myCatchSection)
           && myMoveBeforeSection.isValid()
           && myCatchSection.getCatchType() != null
           && PsiUtil.resolveClassInType(myCatchSection.getCatchType()) != null
           && myMoveBeforeSection.getCatchType() != null
           && PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()) != null
           && !myCatchSection.getManager().areElementsEquivalent(
      PsiUtil.resolveClassInType(myCatchSection.getCatchType()),
      PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()));
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myCatchSection.getContainingFile();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiTryStatement statement = myCatchSection.getTryStatement();
    statement.addBefore(myCatchSection, myMoveBeforeSection);
    myCatchSection.delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
