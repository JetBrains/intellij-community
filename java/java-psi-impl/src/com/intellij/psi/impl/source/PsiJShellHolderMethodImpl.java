// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtilRt;
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
  private static final Logger LOG = Logger.getInstance(PsiJShellHolderMethodImpl.class);

  private final String myName;
  private PsiParameterList myParameterList;
  private PsiReferenceList myThrowsList;

  public PsiJShellHolderMethodImpl(@NotNull ASTNode node, int index) {
    super(node);
    myName = "_$$jshell_holder_method$$" + index;
  }

  @Override
  public PsiElement @NotNull [] getStatements() {
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
    return PsiTypes.voidType();
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
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
      myParameterList = elementFactory.createParameterList(ArrayUtilRt.EMPTY_STRING_ARRAY, PsiType.EMPTY_ARRAY);
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
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
    try {
      myThrowsList = elementFactory.createReferenceList(new PsiJavaCodeReferenceElement[]{
        elementFactory.createFQClassNameReferenceElement(CommonClassNames.JAVA_LANG_THROWABLE, getResolveScope())
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
    PsiElement child = getFirstChild();
    return child instanceof PsiCodeBlock? (PsiCodeBlock)child : null;
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

  @Override
  public PsiMethod @NotNull [] findSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(boolean checkAccess) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(PsiClass parentClass) {
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

  @Override
  public PsiMethod @NotNull [] findDeepestSuperMethods() {
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

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
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
