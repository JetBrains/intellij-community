// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsiTypeParameterImpl extends JavaStubPsiElement<PsiTypeParameterStub> implements PsiTypeParameter {
  public PsiTypeParameterImpl(final PsiTypeParameterStub stub) {
    super(stub, JavaStubElementTypes.TYPE_PARAMETER);
  }

  public PsiTypeParameterImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  public @Unmodifiable @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  public @Unmodifiable @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return getParent().getParent();
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    if (!(parentParent instanceof PsiTypeParameterListOwner)) {
      // Might be an error element;
      return null;
    }

    return (PsiTypeParameterListOwner)parentParent;
  }


  @Override
  public int getIndex() {
    final PsiTypeParameterStub stub = getGreenStub();
    if (stub != null) {
      final PsiTypeParameterListStub parentStub = (PsiTypeParameterListStub)stub.getParentStub();
      return parentStub.getChildrenStubs().indexOf(stub);
    }

    int ret = 0;
    PsiElement element = getPrevSibling();
    while (element != null) {
      if (element instanceof PsiTypeParameter) {
        ret++;
      }
      element = element.getPrevSibling();
    }
    return ret;
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiIdentifier.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  public String getName() {
    final PsiTypeParameterStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }

    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public @NotNull PsiReferenceList getExtendsList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.EXTENDS_BOUND_LIST, PsiReferenceList.class);
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return new LightEmptyImplementsList(getManager());
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NonNls String toString() {
    return "PsiTypeParameter:" + getName();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getStubOrPsiChildren(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }
}
