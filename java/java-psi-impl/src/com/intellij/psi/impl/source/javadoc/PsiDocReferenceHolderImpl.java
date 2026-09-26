// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiDelegateReference;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.javadoc.PsiDocReferenceHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class PsiDocReferenceHolderImpl extends LazyParseablePsiElement implements PsiDocReferenceHolder {
  public PsiDocReferenceHolderImpl(CharSequence text) {
    super(JavaDocElementType.DOC_REFERENCE_HOLDER, text);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocReferenceHolder(this);
    }
    super.accept(visitor);
  }

  @Override
  public PsiReference getReference() {
    return new PsiDelegateReference(PsiDocMethodOrFieldRef.getReference(this)) {
      @Override
      public @Nullable PsiElement resolve() {
        PsiReference childRef = PsiDocReferenceHolderImpl.this.getFirstChild().getReference();
        if (childRef != null && childRef.resolve() != null) {
          return null;
        }
        return super.resolve();
      }
    };
  }
}
