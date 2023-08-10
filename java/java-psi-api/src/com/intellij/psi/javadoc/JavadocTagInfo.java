// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.javadoc;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 *  Specification for JavaDoc tag. Allow to suggest it in completion and check its validity.
 */
public interface JavadocTagInfo {
  ExtensionPointName<JavadocTagInfo> EP_NAME = ExtensionPointName.create("com.intellij.javadocTagInfo");

  /**
   * @return name of the tag that will be shown in completion.
   */
  String getName();

  /**
   * Is inline tag
   */
  boolean isInline();

  /**
   * Checks if given tag is valid in the context. In this context tag will be suggested in completion.
   * If it is invalid in given context then it will be highlighted.
   *
   * @param element element which owns JavaDoc (for example {@link com.intellij.psi.PsiMethod})
   */
  boolean isValidInContext(PsiElement element);

  /**
   * Checks the tag value for correctness.
   *
   * @param value Doc tag to check.
   * @return Returns null if correct, error message otherwise.
   */
  @Nullable @Nls
  String checkTagValue(PsiDocTagValue value);

  /**
   * Provides reference for the tag.
   * @param value Doc tag which may hold reference
   */
  @Nullable
  PsiReference getReference(PsiDocTagValue value);
}