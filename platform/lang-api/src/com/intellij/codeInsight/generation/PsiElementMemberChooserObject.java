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

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiElementMemberChooserObject extends MemberChooserObjectBase {
  private final @NotNull PsiElement myPsiElement;
  private final @NotNull SmartPsiElementPointer<?> myPsiElementPointer;

  public PsiElementMemberChooserObject(@NotNull final PsiElement psiElement, final @NlsContexts.Label String text) {
    this(psiElement, text, null);
  }

  public PsiElementMemberChooserObject(@NotNull PsiElement psiElement, final @NlsContexts.Label String text, @Nullable final Icon icon) {
    super(text, icon);
    myPsiElement = psiElement;
    myPsiElementPointer = SmartPointerManager.createPointer(myPsiElement);
  }

  /**
   * @return PsiElement associated with this object. May return invalid element if the element was invalidated and cannot be restored
   * via smart pointer.
   */
  @NotNull
  public PsiElement getPsiElement() {
    PsiElement element = myPsiElementPointer.getElement();
    return element == null ?
           myPsiElement : // to at least get invalidation trace in PIEAE later 
           element;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementMemberChooserObject that = (PsiElementMemberChooserObject)o;

    return myPsiElementPointer.equals(that.myPsiElementPointer);
  }

  public int hashCode() {
    return myPsiElementPointer.hashCode();
  }
}
