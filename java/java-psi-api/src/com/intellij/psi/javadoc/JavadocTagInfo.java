// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.javadoc;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public interface JavadocTagInfo {
  ExtensionPointName<JavadocTagInfo> EP_NAME = ExtensionPointName.create("com.intellij.javadocTagInfo");

  String getName();

  boolean isInline();

  boolean isValidInContext(PsiElement element);

  /**
   * Checks the tag value for correctness.
   *
   * @param value Doc tag to check.
   * @return Returns null if correct, error message otherwise.
   */
  @Nullable @Nls
  String checkTagValue(PsiDocTagValue value);

  @Nullable
  PsiReference getReference(PsiDocTagValue value);
}