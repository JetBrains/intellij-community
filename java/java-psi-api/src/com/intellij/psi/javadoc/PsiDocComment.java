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

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a JavaDoc comment.
 */
public interface PsiDocComment extends PsiComment, PsiDocCommentBase {
  /**
   * Returns the class, method or field described by the comment.
   */
  @Override
  @Nullable
  PsiDocCommentOwner getOwner();

  /**
   * Returns the PSI elements containing the description of the element being documented
   * (all significant tokens up to the first doc comment tag).
   */
  @NotNull
  PsiElement[] getDescriptionElements();

  /**
   * Returns the list of JavaDoc tags in the comment.
   */
  @NotNull
  PsiDocTag[] getTags();

  /**
   * Finds the first JavaDoc tag with the specified name.
   * @param name The name of the tags to find (not including the leading @ character).
   * @return the tag with the specified name, or null if not found.
   */
  @Nullable
  PsiDocTag findTagByName(@NonNls String name);

  /**
   * Finds all JavaDoc tags with the specified name.
   * @param name The name of the tags to find (not including the leading @ character).
   */
  @NotNull
  PsiDocTag[] findTagsByName(@NonNls String name);
}