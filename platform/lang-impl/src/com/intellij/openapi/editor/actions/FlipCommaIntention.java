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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
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
    return comma != null && smartAdvance(comma, true) != null && smartAdvance(comma, false) != null;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    final PsiElement element = currentCommaElement(editor, file);
    if (element != null) {
      swapAtComma(element);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void swapAtComma(@NotNull PsiElement comma) {
    PsiElement prev = smartAdvance(comma, false);
    PsiElement next = smartAdvance(comma, true);
    if (prev != null && next != null) {
      if (Flipper.tryFlip(prev, next)) {
        return;
      }
      PsiElement copy = prev.copy();
      prev.replace(next);
      next.replace(copy);
    }
  }

  public interface Flipper {
    LanguageExtension<Flipper> EXTENSION = new LanguageExtension<>("com.intellij.flipCommaIntention.flipper");

    /**
     * @return true, if elements were flipped; false, if default flip implementation should be used.
     */
    boolean flip(PsiElement left, PsiElement right);

    static boolean tryFlip(PsiElement left, PsiElement right) {
      final Language language = left.getLanguage();
      for (Flipper handler : EXTENSION.allForLanguage(language)) {
        if (handler.flip(left, right)) {
          return true;
        }
      }
      return false;
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
}
