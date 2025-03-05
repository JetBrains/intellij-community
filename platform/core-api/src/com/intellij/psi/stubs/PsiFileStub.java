// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.StubFileElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiFileStub<T extends PsiFile> extends StubElement<T>, UserDataHolder {
  PsiFileStub<?>[] EMPTY_ARRAY = new PsiFileStub[0];

  /**
   * @deprecated use {@link #getFileElementType()}
   */
  @Deprecated
  @NotNull
  StubFileElementType<?> getType();

  @ApiStatus.Experimental
  default @NotNull IElementType getFileElementType() {
    return getType();
  }

  @Nullable
  String getInvalidationReason();
}
