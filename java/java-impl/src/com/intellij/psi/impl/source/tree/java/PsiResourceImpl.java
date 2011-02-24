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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

public class PsiResourceImpl extends CompositePsiElement implements PsiResource {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiResourceImpl");

  public PsiResourceImpl() {
    super(JavaElementType.RESOURCE);
  }

  @NotNull
  @Override
  public PsiElement getResourceElement() {
    final PsiElement element = getFirstChild();
    assert element != null : this;
    return element;
  }

  @Override
  public PsiType getType() {
    final PsiElement element = getResourceElement();
    if (element instanceof PsiLocalVariable) {
      return ((PsiLocalVariable)element).getType();
    }
    else if (element instanceof PsiExpression) {
      return ((PsiExpression)element).getType();
    }
    LOG.error("Unexpected resource type: " + LogUtil.objectAndClass(element));
    return null;
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitResource(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String getName() {
    final PsiElement element = getFirstChild();
    if (element instanceof PsiLocalVariable) return ((PsiLocalVariable)element).getName();
    if (element instanceof PsiAssignmentExpression) return ((PsiAssignmentExpression)element).getLExpression().toString();
    return "";
  }

  @Override
  public String toString() {
    return "PsiResource:" + getName();
  }
}
