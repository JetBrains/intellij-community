// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface OverrideMethodsProcessor {
  ExtensionPointName<OverrideMethodsProcessor> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.overrideMethodProcessor");

  /**
   * Should check if {@code element} has override attribute and iff, then remove it.
   * @return true if attribute was found so further processing is not required
   */
  boolean removeOverrideAttribute(@NotNull PsiElement element);
}
