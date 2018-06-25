/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Medvedev Max
 */
public interface GenerationInfo {
  GenerationInfo[] EMPTY_ARRAY = new GenerationInfo[0];

  void insert(@NotNull PsiClass aClass, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException;

  @NotNull
  PsiMember getPsiMember();

  default boolean isMemberValid() {
    return true;
  }

  /**
   * @param leaf leaf element. Is guaranteed to be a tree descendant of aClass.
   * @return the value that will be passed to the {@link #insert(PsiClass, PsiElement, boolean)} method later.
   */
  @Nullable
  PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf);

  /**
   * Position caret in generated element in correct way
   */
  void positionCaret(@NotNull Editor editor, boolean toEditMethodBody);
}