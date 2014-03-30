/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class ClsMemberImpl<T extends NamedStub> extends ClsRepositoryPsiElement<T> implements PsiDocCommentOwner, PsiNameIdentifierOwner {
  private final NotNullLazyValue<PsiDocComment> myDocComment;
  private final NotNullLazyValue<PsiIdentifier> myNameIdentifier;

  protected ClsMemberImpl(T stub) {
    super(stub);
    myDocComment = !isDeprecated() ? null : new AtomicNotNullLazyValue<PsiDocComment>() {
      @NotNull
      @Override
      protected PsiDocComment compute() {
        return new ClsDocCommentImpl(ClsMemberImpl.this);
      }
    };
    myNameIdentifier = new AtomicNotNullLazyValue<PsiIdentifier>() {
      @NotNull
      @Override
      protected PsiIdentifier compute() {
        return new ClsIdentifierImpl(ClsMemberImpl.this, getName());
      }
    };
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
