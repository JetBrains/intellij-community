/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiElementMemberChooserObject extends MemberChooserObjectBase {
  private final PsiElement myPsiElement;

  public PsiElementMemberChooserObject(@NotNull final PsiElement psiElement, final String text) {
    super(text);
    myPsiElement = psiElement;
  }

  public PsiElementMemberChooserObject(@NotNull PsiElement psiElement, final String text, @Nullable final Icon icon) {
    super(text, icon);
    myPsiElement = psiElement;
  }

  @NotNull
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementMemberChooserObject that = (PsiElementMemberChooserObject)o;

    if (!myPsiElement.getManager().areElementsEquivalent(myPsiElement, that.myPsiElement)) return false;

    return true;
  }

  public int hashCode() {
    return myPsiElement.hashCode();
  }
}
