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
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class PsiJShellHolderMethodImpl extends ASTWrapperPsiElement implements PsiJShellHolderMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiJShellHolderMethodImpl");

  private final String myName;
  private PsiParameterList myParameterList;
  private PsiReferenceList myThrowsList;

  public PsiJShellHolderMethodImpl(@NotNull ASTNode node, int index) {
    super(node);
    myName = "_$$jshell_holder_method$$" + index;
  }

  @NotNull
  @Override
  public PsiElement[] getStatements() {
    List<PsiElement> result = null;
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiStatement || child instanceof PsiExpression) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(child);
      }
    }
    return result == null? PsiElement.EMPTY_ARRAY : result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public PsiType getReturnType() {
    return PsiType.VOID;
  }

  @Nullable
  @Override
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  @Override
  public PsiParameterList getParameterList() {
    if (myParameterList != null) {
      return myParameterList;
    }
    try {
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      myParameterList = elementFactory.createParameterList(ArrayUtil.EMPTY_STRING_ARRAY, PsiType.EMPTY_ARRAY);
      return myParameterList;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  @Override
  public PsiReferenceList getThrowsList() {
    if (myThrowsList != null) {
      return myThrowsList;
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    try {
      myThrowsList = elementFactory.createReferenceList(new PsiJavaCodeReferenceElement[]{
        elementFactory.createFQClassNameReferenceElement("java.lang.Throwable", getResolveScope())
      });
      return myThrowsList;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)getFirstChild();
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  @Override
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, PsiSubstitutor.EMPTY);
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  @Override
  public PsiMethod[] findSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  @Override
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiModifierList getModifierList() {
    return new LightModifierList(getManager());
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't change name of JShell holder method");
  }

  @NotNull
  @Override
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @NotNull
  @Override
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }
}
