// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiSymbolReferenceHints {

  /**
   * Provider may return only references which could be resolved to symbols of this type if the type is not {@code null}.
   *
   * @return type of expected target symbol
   */
  @Nullable
  default Class<? extends Symbol> getTargetClass() {
    Symbol target = getTarget();
    return target != null ? target.getClass() : null;
  }

  /**
   * Provider may return only references which could be resolved to specified symbol if the symbol is not {@code null}.
   *
   * @return expected target symbol
   */
  @Nullable
  default Symbol getTarget() {
    return null;
  }

  /**
   * Provider may return references which contain specified offset if the offset is greater than or equal to 0;
   * in this case the offset is guaranteed to be within {@code [0, element.getTextLength())}.
   *
   * @return offset in the element for which references are queried, or {@code null} if the offset doesn't matter
   */
  @Nullable
  default Integer getOffsetInElement() {
    return null;
  }

  @NotNull
  static PsiSymbolReferenceHints offsetHint(int offsetInElement) {
    assert offsetInElement >= 0;
    return new PsiSymbolReferenceHints() {
      @Override
      public Integer getOffsetInElement() {
        return offsetInElement;
      }
    };
  }
}
