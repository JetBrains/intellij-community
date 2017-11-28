/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DeleteMultiCatchFix implements IntentionAction {
  private final PsiTypeElement myTypeElement;

  public DeleteMultiCatchFix(@NotNull PsiTypeElement typeElement) {
    myTypeElement = typeElement;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("delete.catch.text", JavaHighlightUtil.formatType(myTypeElement.getType()));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("delete.catch.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myTypeElement.isValid() && PsiManager.getInstance(project).isInProject(myTypeElement.getContainingFile());
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myTypeElement;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement parentType = myTypeElement.getParent();
    if (!(parentType instanceof PsiTypeElement)) return;

    final PsiElement first;
    final PsiElement last;
    final PsiElement right = PsiTreeUtil.skipWhitespacesAndCommentsForward(myTypeElement);
    if (PsiUtil.isJavaToken(right, JavaTokenType.OR)) {
      first = myTypeElement;
      last = right;
    }
    else if (right == null) {
      final PsiElement left = PsiTreeUtil.skipWhitespacesAndCommentsBackward(myTypeElement);
      if (!(left instanceof PsiJavaToken)) return;
      final IElementType leftType = ((PsiJavaToken)left).getTokenType();
      if (leftType != JavaTokenType.OR) return;
      first = left;
      last = myTypeElement;
    }
    else {
      return;
    }

    parentType.deleteChildRange(first, last);

    final List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(parentType, PsiTypeElement.class);
    if (typeElements.size() == 1) {
      final PsiElement parameter = parentType.getParent();
      parameter.addRangeAfter(parentType.getFirstChild(), parentType.getLastChild(), parentType);
      parentType.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
