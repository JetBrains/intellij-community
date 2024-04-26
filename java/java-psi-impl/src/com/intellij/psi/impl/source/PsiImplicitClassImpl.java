// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.JavaImplicitClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class PsiImplicitClassImpl extends JavaStubPsiElement<PsiClassStub<?>> implements PsiImplicitClass, PsiExtensibleClass {
  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

  public PsiImplicitClassImpl(@NotNull PsiClassStub<?> stub) {
    super(stub, JavaStubElementTypes.IMPLICIT_CLASS);
  }

  public PsiImplicitClassImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable String getQualifiedName() {
    PsiElement parent = getParent();
    if (parent instanceof PsiJavaFile) {
      return JavaImplicitClassUtil.getJvmName(((PsiJavaFile)parent).getName());
    }
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
  public @Nullable PsiReferenceList getExtendsList() {
    return new LightEmptyImplementsList(getManager());
  }

  @Override
  public @Nullable PsiReferenceList getImplementsList() {
    return new LightReferenceListBuilder(getManager(), PsiReferenceList.Role.EXTENDS_LIST);
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public @Nullable PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, getResolveScope());
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass javaLangObject = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject()));
    if (javaLangObject == null) return PsiClass.EMPTY_ARRAY;
    return new PsiClass[]{javaLangObject};
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return new PsiClassType[] { PsiType.getJavaLangObject(getManager(), getResolveScope()) };
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public @Nullable PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  @Override
  public @Nullable PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public @Nullable PsiClass findInnerClassByName(String name, boolean checkBases) {
    return myInnersCache.findInnerClassByName(name, checkBases);
  }

  @Override
  public @Nullable PsiElement getLBrace() {
    return null;
  }

  @Override
  public @Nullable PsiElement getRBrace() {
    return null;
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement getScope() {
    return getParent();
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public @Nullable PsiClass getContainingClass() {
    return null;
  }

  @Override
  public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Implicitly declared class may have no name");
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public @Nullable PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return name.equals(PsiModifier.FINAL) || name.equals(PsiModifier.PACKAGE_LOCAL);
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  public @Nullable PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public @NotNull List<PsiField> getOwnFields() {
    return Arrays.asList(getStubOrPsiChildren(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @Override
  public @NotNull List<PsiMethod> getOwnMethods() {
    return Arrays.asList(getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @Override
  public @NotNull List<PsiClass> getOwnInnerClasses() {
    return Arrays.asList(getStubOrPsiChildren(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY));
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(getContainingFile());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImplicitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    LanguageLevel level = PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @Override
  public Icon getElementIcon(int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  public String toString() {
    return "PsiImplicitClass";
  }
}
