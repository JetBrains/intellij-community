// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR =
    (project, text) -> JavaPsiFacade.getElementFactory(project).createAnnotationFromText(text, null);

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
  public @Nullable PsiAnnotationMemberValue findDeclaredAttributeValue(final @NonNls String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    @SuppressWarnings("unchecked") T t = (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
    return t;
  }

  @Override
  public String toString() {
    return "PsiAnnotation";
  }

  @Override
  public @NotNull PsiAnnotationParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST, PsiAnnotationParameterList.class);
  }

  @Override
  public @Nullable String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  private @Nullable String getShortName() {
    PsiAnnotationStub stub = getStub();
    if (stub != null) {
      return getAnnotationShortName(stub.getText());
    }

    PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    return nameRef == null ? null : nameRef.getReferenceName();
  }


  @Override
  public boolean hasQualifiedName(@NotNull String qualifiedName) {
    return StringUtil.getShortName(qualifiedName).equals(getShortName()) && PsiAnnotation.super.hasQualifiedName(qualifiedName);
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
  public @Nullable PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();

    if (parent instanceof PsiTypeElementImpl) {
      PsiType type = ((PsiTypeElement)parent).getType();
      if (type instanceof PsiClassType) {
        // If we have a type element like @Anno Outer.Inner then the annotation belongs to the Outer type
        // which doesn't have a corresponding type element at all. We create this type here.
        PsiJavaCodeReferenceElement origRef = ((PsiTypeElement)parent).getInnermostComponentReferenceElement();
        PsiJavaCodeReferenceElement ref = origRef;
        while (ref != null && ref.isQualified()) {
          ref = ObjectUtils.tryCast(ref.getQualifier(), PsiJavaCodeReferenceElement.class);
        }
        if (ref != null && ref != origRef) {
          return new PsiClassReferenceType(ref, null).annotate(type.getAnnotationProvider());
        }
      }
      else if (type instanceof PsiArrayType) {
        for (PsiElement sibling = getPrevSibling(); sibling != null; sibling = sibling.getPrevSibling()) {
          if (PsiUtil.isJavaToken(sibling, JavaTokenType.LBRACKET)) {
            type = ((PsiArrayType)type).getComponentType();
          }
        }
      }
      return type;
    }
    
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

  public static @NotNull String getAnnotationShortName(@NotNull String annoText) {
    int at = annoText.indexOf('@');
    int paren = annoText.indexOf('(');
    String qualified = PsiNameHelper.getQualifiedClassName(annoText.substring(at + 1, paren > 0 ? paren : annoText.length()), true);
    return StringUtil.getShortName(qualified);
  }
}
