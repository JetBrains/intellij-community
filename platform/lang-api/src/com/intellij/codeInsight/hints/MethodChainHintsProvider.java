// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public interface MethodChainHintsProvider {
  /**
   * Hints for calls to be shown, offsets should be after calls on each line where a hint should be provided
   */
  @NotNull
  List<InlayInfo> getMethodChainHints(@NotNull PsiElement element, @NotNull Editor editor);
}
