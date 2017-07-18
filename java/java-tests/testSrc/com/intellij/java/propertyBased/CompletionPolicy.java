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
package com.intellij.java.propertyBased;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionPolicy {

  /**
   * @return the lookup string of an element that should be suggested in the given position
   */
  public String getExpectedVariant(Editor editor, PsiFile file) {
    PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
    PsiElement refTarget = ref == null ? null : ref.resolve();
    if (leaf == null) {
      return null;
    }
    String leafText = leaf.getText();
    if (leafText.isEmpty() ||
        !Character.isLetter(leafText.charAt(0)) ||
        leaf instanceof PsiWhiteSpace ||
        PsiTreeUtil.getParentOfType(leaf, PsiComment.class, false) != null) {
      return null;
    }
    if (ref != null) {
      if (refTarget == null || !shouldSuggestReferenceText(ref)) return null;
    }
    else {
      if (!SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).isEmpty()) {
        return null;
      }
      if (!shouldSuggestNonReferenceLeafText(leaf)) return null;
    }
    return leafText;

  }

  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    return true;
  }

  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref) { 
    return true;
  }
}
