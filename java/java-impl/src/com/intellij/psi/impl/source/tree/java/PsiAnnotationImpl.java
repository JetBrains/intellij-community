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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR = new PairFunction<Project, String, PsiAnnotation>() {
    public PsiAnnotation fun(Project project, String text) {
      return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(text, null);
    }
  };

  public PsiAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION);
  }

  public PsiAnnotationImpl(final ASTNode node) {
    super(node);
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return (PsiJavaCodeReferenceElement)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
  }

  private CompositeElement getMirrorTreeElement() {
    final PsiAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getTreeElement();
    }

    return (CompositeElement)getNode();
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return (PsiAnnotationParameterList)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
  }

  @Nullable public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    if (parent instanceof PsiTypeElement) {
      return ((PsiTypeElement)parent).getOwner(this);
    }
    if (parent instanceof PsiMethodReceiver || parent instanceof PsiTypeParameter) return (PsiAnnotationOwner)parent;
    PsiElement member = parent.getParent();
    String[] elementTypeFields = AnnotationsHighlightUtil.getApplicableElementTypeFields(member);
    if (elementTypeFields == null) return null;
    if (parent instanceof PsiAnnotationOwner
        && AnnotationsHighlightUtil.isAnnotationApplicableTo(this, true, elementTypeFields)) return (PsiAnnotationOwner)parent;

    PsiAnnotationOwner typeElement;
    if (member instanceof PsiVariable) {
      typeElement = ((PsiVariable)member).getTypeElement();
    }
    else if (member instanceof PsiMethod) {
      typeElement = ((PsiMethod)member).getReturnTypeElement();
    }
    else if (parent instanceof PsiAnnotationOwner) {
      typeElement = (PsiAnnotationOwner)parent;
    }
    else {
      typeElement = null;
    }
    return typeElement;
  }
}