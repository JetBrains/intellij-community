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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceTreeToPsiMap {
  private SourceTreeToPsiMap() { }

  @Nullable
  public static PsiElement treeElementToPsi(@Nullable final ASTNode element) {
    return element == null ? null : element.getPsi();
  }

  @NotNull
  public static <T extends PsiElement> T treeToPsiNotNull(@NotNull final ASTNode element) {
    final PsiElement psi = element.getPsi();
    assert psi != null : element;
    //noinspection unchecked
    return (T)psi;
  }

  @Nullable
  public static ASTNode psiElementToTree(@Nullable final PsiElement psiElement) {
    return psiElement == null ? null : psiElement.getNode();
  }

  @NotNull
  public static TreeElement psiToTreeNotNull(@NotNull final PsiElement psiElement) {
    final ASTNode node = psiElement.getNode();
    assert node instanceof TreeElement : psiElement + ", " + node;
    return (TreeElement)node;
  }

  public static boolean hasTreeElement(@Nullable final PsiElement psiElement) {
    return psiElement instanceof TreeElement || psiElement instanceof ASTDelegatePsiElement || psiElement instanceof PsiFileImpl;
  }
}
