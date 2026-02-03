// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * Element which can be used in {@link PsiCaseLabelElementList}
 */
@ApiStatus.NonExtendable
public interface PsiCaseLabelElement extends PsiElement {
  PsiCaseLabelElement[] EMPTY_ARRAY = new PsiCaseLabelElement[0];

  ArrayFactory<PsiCaseLabelElement> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiCaseLabelElement[count];
}
