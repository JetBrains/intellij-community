// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiMemberStub;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class ClsMemberImpl<T extends PsiMemberStub> extends ClsRepositoryPsiElement<T> implements PsiDocCommentOwner, PsiNameIdentifierOwner {
  private final NotNullLazyValue<PsiDocComment> myDocComment;
  private final NotNullLazyValue<PsiIdentifier> myNameIdentifier;

  protected ClsMemberImpl(T stub) {
    super(stub);
    myDocComment = !stub.isDeprecated() ? null : NotNullLazyValue.atomicLazy(() -> {
      return new ClsDocCommentImpl(ClsMemberImpl.this);
    });
    myNameIdentifier = NotNullLazyValue.atomicLazy(() -> {
      return new ClsIdentifierImpl(ClsMemberImpl.this, getName());
    });
  }

  @Override
  public PsiDocComment getDocComment() {
    return myDocComment != null ? myDocComment.getValue() : null;
  }

  @Override
  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier.getValue();
  }

  @Override
  @NotNull
  public String getName() {
    //noinspection ConstantConditions
    return getStub().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }
}
