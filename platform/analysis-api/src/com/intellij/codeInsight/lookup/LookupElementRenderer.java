// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;

/**
 * Renderer that is used for expensive renderinf of {@link LookupElement}s.
 *
 * @see LookupElement#getExpensiveRenderer()
 * @see SuspendingLookupElementRenderer
 */
public abstract class LookupElementRenderer<T extends LookupElement> {
  public abstract void renderElement(@NotNull T element, @NotNull LookupElementPresentation presentation);
}
