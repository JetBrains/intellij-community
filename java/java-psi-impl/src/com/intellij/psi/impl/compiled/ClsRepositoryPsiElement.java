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
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ClsRepositoryPsiElement<T extends StubElement> extends ClsElementImpl implements StubBasedPsiElement<T> {
  private final T myStub;

  protected ClsRepositoryPsiElement(final T stub) {
    myStub = stub;
  }

  @Override
  public IStubElementType getElementType() {
    return myStub.getStubType();
  }

  @Override
  public PsiElement getParent() {
    return myStub.getParentStub().getPsi();
  }

  @Override
  public PsiManager getManager() {
    final PsiFile file = getContainingFile();
    if (file == null) throw new PsiInvalidElementAccessException(this);
    return file.getManager();
  }

  @Override
  public PsiFile getContainingFile() {
    StubElement p = myStub;
    while (!(p instanceof PsiFileStub)) {
      p = p.getParentStub();
    }
    return (PsiFile)p.getPsi();
  }

  @Override
  public T getStub() {
    return myStub;
  }

  @Override
  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    final List stubs = getStub().getChildrenStubs();
    PsiElement[] children = new PsiElement[stubs.size()];
    for (int i = 0; i < stubs.size(); i++) {
      children[i] = ((StubElement)stubs.get(i)).getPsi();
    }
    return children;
  }

  @Override
  public PsiElement getFirstChild() {
    final List<StubElement> children = getStub().getChildrenStubs();
    if (children.isEmpty()) return null;
    return children.get(0).getPsi();
  }

  @Override
  public PsiElement getLastChild() {
    final List<StubElement> children = getStub().getChildrenStubs();
    if (children.isEmpty()) return null;
    return children.get(children.size() - 1).getPsi();
  }

  @Override
  public PsiElement getNextSibling() {
    final PsiElement[] psiElements = getParent().getChildren();
    final int i = ArrayUtil.indexOf(psiElements, this);
    if (i < 0 || i >= psiElements.length - 1) {
      return null;
    }
    return psiElements[i + 1];
  }


  @Override
  public PsiElement getPrevSibling() {
    final PsiElement[] psiElements = getParent().getChildren();
    final int i = ArrayUtil.indexOf(psiElements, this);
    if (i < 1) {
      return null;
    }
    return psiElements[i - 1];
  }
}
