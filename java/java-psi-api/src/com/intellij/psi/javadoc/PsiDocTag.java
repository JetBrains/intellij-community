/*
 * Copyright 2000-2015 JetBrains s.r.o.
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


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a JavaDoc tag (either an inline tag or a block tag).
 */
public interface PsiDocTag extends PsiElement, PsiNamedElement {
  PsiDocTag[] EMPTY_ARRAY = new PsiDocTag[0];

  /**
   * Returns the doc comment in which the tag is contained.
   */
  PsiDocComment getContainingComment();

  /**
   * Returns the token representing the name of this JavaDoc tag.
   */
  PsiElement getNameElement();

  /**
   * Returns the name of this JavaDoc tag.
   */
  @Override
  @NonNls @NotNull String getName();

  /**
   * Returns the list of all elements representing the contents of a tag.
   */
  PsiElement[] getDataElements();

  /**
   * Returns the element specifying what exactly is being documented by this tag
   * (for example, the parameter name for a param tag or the exception name for a throws tag).
   *
   * @return the element, or null if the tag structure does not include such an element.
   */
  @Nullable PsiDocTagValue getValueElement();
}