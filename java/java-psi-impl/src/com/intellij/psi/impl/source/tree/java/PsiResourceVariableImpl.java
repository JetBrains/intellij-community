/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiResourceVariableImpl extends PsiLocalVariableImpl implements PsiResourceVariable {
  public PsiResourceVariableImpl() {
    super(JavaElementType.RESOURCE_VARIABLE);
  }

  @NotNull
  @Override
  public PsiElement[] getDeclarationScope() {
    final PsiResourceList resourceList = (PsiResourceList)getParent();
    final PsiTryStatement tryStatement = (PsiTryStatement)resourceList.getParent();
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    return tryBlock != null ? new PsiElement[]{resourceList, tryBlock} : new PsiElement[]{resourceList};
  }

  @NotNull
  @Override
  public PsiTypeElement getTypeElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiTypeElement.class);
  }

  @Override
  public PsiModifierList getModifierList() {
    return PsiTreeUtil.getChildOfType(this, PsiModifierList.class);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    final PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(this);
    if (PsiUtil.isJavaToken(next, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(this, next);
      return;
    }

    final PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(this);
    if (PsiUtil.isJavaToken(prev, JavaTokenType.SEMICOLON)) {
      getParent().deleteChildRange(prev, this);
      return;
    }

    super.delete();
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitResourceVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public String toString() {
    return "PsiResourceVariable:" + getName();
  }
}
