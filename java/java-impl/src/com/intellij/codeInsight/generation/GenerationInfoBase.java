/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class GenerationInfoBase implements GenerationInfo {

  @Override
  public abstract void insert(@NotNull PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException;

  @NotNull
  @Override
  public abstract PsiMember getPsiMember();

  @Override
  @Nullable
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement element = leaf;
    while (element.getParent() != aClass) {
      element = element.getParent();
    }

    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) {
      return null;
    }
    PsiElement rBrace = aClass.getRBrace();
    if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
      return null;
    }
    if (leaf.getParent() == aClass && PsiUtilCore.getElementType(leaf.getPrevSibling()) == JavaTokenType.END_OF_LINE_COMMENT) {
      element = leaf.getNextSibling();
    }
    return element;
  }

  @Override
  public void positionCaret(@NotNull Editor editor, boolean toEditMethodBody) {
    GenerateMembersUtil.positionCaret(editor, getPsiMember(), toEditMethodBody);
  }
}
