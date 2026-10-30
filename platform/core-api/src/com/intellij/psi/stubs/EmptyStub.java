// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

public final class EmptyStub<T extends PsiElement> extends StubBase<T> {
  public EmptyStub(StubElement parent, IElementType elementType) {
    super(parent, elementType);
  }

  public EmptyStub(StubElement parent, IStubElementType elementType) {
    super(parent, elementType);
  }
}
