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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR =
    (project, text) -> JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(text, null);

  public PsiAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION);
  }

  public PsiAnnotationImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    PsiAnnotationStub stub = getStub();
    return PsiTreeUtil.getChildOfType(stub != null ? stub.getPsiElement() : this, PsiJavaCodeReferenceElement.class);
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    @SuppressWarnings("unchecked") T t = (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
    return t;
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @Override
  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST);
  }

  @Override
  @Nullable
  public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  @Nullable
  @Override
  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();

    if (parent instanceof PsiAnnotationOwner) {
      return (PsiAnnotationOwner)parent;
    }

    if (parent instanceof PsiNewExpression) {
      return ((PsiNewExpression)parent).getOwner(this);
    }

    if (parent instanceof PsiReferenceExpression) {
      PsiElement ctx = parent.getParent();
      if (ctx instanceof PsiMethodReferenceExpression) {
        return new PsiClassReferenceType((PsiJavaCodeReferenceElement)parent, null);
      }
    }
    else if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement ctx = PsiTreeUtil.skipParentsOfType(parent, PsiJavaCodeReferenceElement.class);
      if (ctx instanceof PsiReferenceList ||
          ctx instanceof PsiNewExpression ||
          ctx instanceof PsiTypeElement ||
          ctx instanceof PsiAnonymousClass) {
        return new PsiClassReferenceType((PsiJavaCodeReferenceElement)parent, null);
      }
    }

    PsiTypeElement typeElement = null;
    PsiElement anchor = null;
    if (parent instanceof PsiMethod) {
      typeElement = ((PsiMethod)parent).getReturnTypeElement();
      anchor = ((PsiMethod)parent).getParameterList();
    }
    else if (parent instanceof PsiField || parent instanceof PsiParameter || parent instanceof PsiLocalVariable) {
      typeElement = ((PsiVariable)parent).getTypeElement();
      anchor = ((PsiVariable)parent).getNameIdentifier();
    }
    if (typeElement != null && anchor != null) {
      return JavaSharedImplUtil.getType(typeElement, anchor, this);
    }

    return null;
  }
}
