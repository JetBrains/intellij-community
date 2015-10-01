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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlipCommaIntention implements IntentionAction {
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Flip ','";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement comma = currentCommaElement(editor, file);
    return comma != null && smartAdvanceAsExpr(comma, true) != null && smartAdvance(comma, false) != null;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    final PsiElement element = currentCommaElement(editor, file);
    if (element != null) {
      new WriteCommandAction(project, file) {
        protected void run(@NotNull Result result) throws Throwable {
          PostprocessReformattingAspect.getInstance(getProject()).disablePostprocessFormattingInside(new Runnable() {
            @Override
            public void run() {
              swapAtComma(editor, element);
            }
          });
        }
      }.execute();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void swapAtComma(@NotNull Editor editor, @NotNull PsiElement comma) {
    PsiElement prev = smartAdvanceAsExpr(comma, false);
    PsiElement next = smartAdvanceAsExpr(comma, true);
    if (prev != null && next != null) {
      boolean caretBeforeComma = editor.getCaretModel().getOffset() <= comma.getTextRange().getStartOffset();
      PsiElement nextAnchor = next.getPrevSibling();
      PsiElement prevAnchor = prev.getNextSibling();
      comma.getParent().addBefore(next, prevAnchor);
      comma.getParent().addAfter(prev, nextAnchor);
      next.delete();
      prev.delete();
      editor.getCaretModel().moveToOffset(caretBeforeComma ? comma.getTextRange().getStartOffset() : comma.getTextRange().getEndOffset());
    }
  }

  private static PsiElement currentCommaElement(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement element;
    if (!isComma(element = leftElement(editor, file)) && !isComma(element = rightElement(editor, file))) {
      return null;
    }
    return element;
  }

  @Nullable
  private static PsiElement leftElement(@NotNull Editor editor, @NotNull PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset() - 1);
  }

  @Nullable
  private static PsiElement rightElement(@NotNull Editor editor, @NotNull PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset());
  }

  private static boolean isComma(@Nullable PsiElement element) {
    return element != null && element.getText().equals(",");
  }

  @Nullable
  private static PsiElement smartAdvance(PsiElement element, boolean fwd) {
    Class[] skipTypes = {PsiWhiteSpace.class, PsiComment.class};
    return fwd ? PsiTreeUtil.skipSiblingsForward(element, skipTypes)
               : PsiTreeUtil.skipSiblingsBackward(element, skipTypes);
  }

  @Nullable
  static private PsiElement smartAdvanceAsExpr(PsiElement element, boolean fwd) {
    return ObjectUtils.tryCast(smartAdvance(element, fwd), PsiElement.class);
  }
}
