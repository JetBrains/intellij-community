// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.psi.PsiReferenceList.Role.EXTENDS_LIST;
import static com.intellij.psi.PsiReferenceList.Role.IMPLEMENTS_LIST;

public class LightPsiClassBuilder extends LightPsiClassBase implements OriginInfoAwareElement {

  private final LightModifierList myModifierList = new LightModifierList(getManager());
  private final LightReferenceListBuilder myImplementsList = new LightReferenceListBuilder(getManager(), IMPLEMENTS_LIST);
  private final LightReferenceListBuilder myExtendsList = new LightReferenceListBuilder(getManager(), EXTENDS_LIST);
  private final LightTypeParameterListBuilder myTypeParametersList = new LightTypeParameterListBuilder(getManager(), getLanguage());
  private final Collection<PsiMethod> myMethods = new ArrayList<>();
  private PsiElement myScope;
  private PsiClass myContainingClass;
  private String myOriginInfo;

  public LightPsiClassBuilder(@NotNull PsiElement context, @NotNull String name) {
    super(context, name);
  }

  @Override
  public @Nullable String getOriginInfo() {
    return myOriginInfo;
  }

  @Override
  public @NotNull LightModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public @NotNull LightReferenceListBuilder getExtendsList() {
    return myExtendsList;
  }

  @Override
  public @NotNull LightReferenceListBuilder getImplementsList() {
    return myImplementsList;
  }

  @Override
  public PsiField @NotNull [] getFields() {
    // TODO
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myMethods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    // TODO
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getScope() {
    return myScope;
  }

  @Override
  public @Nullable PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public @NotNull LightTypeParameterListBuilder getTypeParameterList() {
    return myTypeParametersList;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  public LightPsiClassBuilder setOriginInfo(String originInfo) {
    myOriginInfo = originInfo;
    return this;
  }

  public LightPsiClassBuilder setScope(PsiElement scope) {
    myScope = scope;
    return this;
  }

  public LightPsiClassBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public LightPsiClassBuilder addMethod(PsiMethod method) {
    if (method instanceof LightMethodBuilder) {
      ((LightMethodBuilder)method).setContainingClass(this);
    }
    myMethods.add(method);
    return this;
  }
}
