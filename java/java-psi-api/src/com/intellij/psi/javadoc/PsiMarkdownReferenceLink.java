// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/** Describes a markdown reference link */
public interface PsiMarkdownReferenceLink extends PsiElement {
  /** @return The PsiElement that acts as a label. On short links, returns the same as {@link #getValueElement()} */
  @Nullable PsiElement getLabel();

  /** @return The PsiElement that act as a reference. */
  @Nullable PsiElement getLinkElement();

  /** @return Whether the link is a short form reference link */
  boolean isShortLink();
}
