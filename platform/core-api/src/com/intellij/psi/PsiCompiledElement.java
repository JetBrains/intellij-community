// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an element in a binary library used in a project. References
 * to library classes/methods are always resolved to compiled elements;
 * to get the corresponding source code, if it is available,
 * {@link PsiElement#getNavigationElement()} should be called.
 */
public interface PsiCompiledElement extends PsiElement {
  /**
   * Returns the corresponding PSI element in a decompiled file created by the IDE from
   * the library element.
   *
   * @return the counterpart of the element in the decompiled file.
   */
  PsiElement getMirror();

  /**
   * @return a mirror which was obtained via {@link #getMirror()} some time ago, if somebody bothered to called it.
   * There are no guarantees about lifecycle of this cached element and this method could return null any time whenever it feels like.
   * It's guaranteed however that this method doesn't call {@link com.intellij.openapi.fileTypes.BinaryFileDecompiler}
   */
  @ApiStatus.Internal
  @Nullable
  default PsiElement getCachedMirror() {
    return null;
  }
}