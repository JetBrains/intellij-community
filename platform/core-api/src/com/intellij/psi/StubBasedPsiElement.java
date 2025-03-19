// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;

public interface StubBasedPsiElement<Stub extends StubElement> extends PsiElement {
  /**
   * @deprecated use {{@link #getIElementType()}} instead.
   */
  @Deprecated
  IStubElementType getElementType();

  // todo IJPL-562 do we have a better name? getStubElementType to confuse everyone? :lol:
  @ApiStatus.Experimental
  default IElementType getIElementType() {
    return getElementType();
  }

  Stub getStub();
}