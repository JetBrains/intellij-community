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

package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.Nullable;

public class SourceTreeToPsiMap {
  private SourceTreeToPsiMap() {
  }

  public static PsiElement treeElementToPsi(@Nullable ASTNode element) {
    if (element == null) return null;
    return element.getPsi();
  }

  public static ASTNode psiElementToTree(@Nullable PsiElement psiElement) {
    if (psiElement == null) return null;
    return psiElement.getNode();
  }

  public static boolean hasTreeElement(@Nullable PsiElement psiElement) {
    return psiElement instanceof TreeElement || psiElement instanceof ASTDelegatePsiElement || psiElement instanceof PsiFileImpl;
  }
}
