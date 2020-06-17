// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class JavaMethodReferenceElement extends LookupElement implements TypedLookupItem {
  private final PsiMethod myMethod;
  private final PsiElement myRefPlace;
  private final PsiType myType;

  JavaMethodReferenceElement(PsiMethod method, PsiElement refPlace, @Nullable PsiType type) {
    myMethod = method;
    myRefPlace = refPlace;
    myType = type;
  }

  @Override
  public @Nullable PsiType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaMethodReferenceElement)) return false;
    JavaMethodReferenceElement element = (JavaMethodReferenceElement)o;
    return getLookupString().equals(element.getLookupString()) &&
           myRefPlace.equals(element.myRefPlace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getLookupString(), myRefPlace);
  }

  @NotNull
  @Override
  public Object getObject() {
    return myMethod;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myMethod.isConstructor() ? "new" : myMethod.getName();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(myMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    super.renderElement(presentation);
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    if (!(myRefPlace instanceof PsiMethodReferenceExpression)) {
      PsiClass containingClass = Objects.requireNonNull(myMethod.getContainingClass());
      String qualifiedName = Objects.requireNonNull(containingClass.getQualifiedName());

      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      final int startOffset = context.getStartOffset();

      document.insertString(startOffset, qualifiedName + "::");
      JavaCompletionUtil.shortenReference(context.getFile(), startOffset + qualifiedName.length() - 1);
    }
  }
}
