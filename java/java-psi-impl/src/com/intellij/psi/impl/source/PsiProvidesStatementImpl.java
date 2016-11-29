/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiProvidesStatementImpl extends CompositePsiElement implements PsiProvidesStatement {
  public PsiProvidesStatementImpl() {
    super(JavaElementType.PROVIDES_STATEMENT);
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getInterfaceReference() {
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiJavaCodeReferenceElement) return (PsiJavaCodeReferenceElement)child;
      if (PsiUtil.isJavaToken(child, JavaTokenType.WITH_KEYWORD)) break;
    }
    return null;
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getImplementationReference() {
    boolean afterWith = false;
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (afterWith && child instanceof PsiJavaCodeReferenceElement) return (PsiJavaCodeReferenceElement)child;
      if (PsiUtil.isJavaToken(child, JavaTokenType.WITH_KEYWORD)) afterWith = true;
    }
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitProvidesStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiProvidesStatement";
  }
}