// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A place in the code: an element, or a range inside it.
 *
 * @param element        the element of the place
 * @param rangeInElement the range relative to the start of the element; null for the whole element
 */
public record PsiPlace(@NotNull PsiElement element, @Nullable TextRange rangeInElement) {
}
