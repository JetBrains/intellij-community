// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Unused in v2 implementation.
 * @see CtrlMouseData
 */
@Deprecated
@ApiStatus.Internal
public interface CtrlMouseInfo {

  boolean isValid();

  @NotNull List<@NotNull TextRange> getRanges();

  default boolean isNavigatable() {
    return true;
  }

  @NotNull CtrlMouseDocInfo getDocInfo();
}
