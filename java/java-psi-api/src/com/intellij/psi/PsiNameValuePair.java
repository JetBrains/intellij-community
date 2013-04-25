/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single element-value pair of an annotation parameter list.
 *
 * @author ven
 * @see PsiAnnotation
 * @see PsiAnnotationParameterList
 */
public interface PsiNameValuePair extends PsiElement {
  /**
   * The empty array of PSI name/value pairs which can be reused to avoid unnecessary allocations.
   */
  PsiNameValuePair[] EMPTY_ARRAY = new PsiNameValuePair[0];

  ArrayFactory<PsiNameValuePair> ARRAY_FACTORY = new ArrayFactory<PsiNameValuePair>() {
    @NotNull
    @Override
    public PsiNameValuePair[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiNameValuePair[count];
    }
  };

  /**
   * Returns the identifier specifying the name of the element.
   *
   * @return the name identifier, or null if the annotation declaration is incomplete.
   */
  @Nullable
  PsiIdentifier getNameIdentifier();

  /**
   * Returns the name of the element.
   *
   * @return the name, or null if the annotation declaration is incomplete.
   */
  @Nullable @NonNls
  String getName();

  @Nullable
  String getLiteralValue();

  /**
   * Returns the value for the element.
   *
   * @return the value for the element.
   */
  @Nullable
  PsiAnnotationMemberValue getValue();

  @NotNull PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue);
}
