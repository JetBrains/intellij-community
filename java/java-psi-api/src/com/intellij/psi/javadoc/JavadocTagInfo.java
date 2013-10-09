/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.javadoc;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public interface JavadocTagInfo {
  ExtensionPointName<JavadocTagInfo> EP_NAME = ExtensionPointName.create("com.intellij.javadocTagInfo");

  @NonNls String getName();
  boolean isInline();

  boolean isValidInContext(PsiElement element);

  Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix);

  /**
   * Checks the tag value for correctness.
   *
   * @param value Doc tag to check.
   * @return Returns null if correct, error message otherwise.
   */
  @Nullable
  String checkTagValue(PsiDocTagValue value);

  @Nullable
  PsiReference getReference(PsiDocTagValue value);
}
