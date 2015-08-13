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
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.psi.PsiReferenceList.Role.EXTENDS_LIST;
import static com.intellij.psi.PsiReferenceList.Role.IMPLEMENTS_LIST;

public class LightPsiClassBuilder extends LightPsiClassBase implements OriginInfoAwareElement {

  private final LightModifierList myModifierList = new LightModifierList(getManager());
  private final LightReferenceListBuilder myImplementsList = new LightReferenceListBuilder(getManager(), IMPLEMENTS_LIST);
  private final LightReferenceListBuilder myExtendsList = new LightReferenceListBuilder(getManager(), EXTENDS_LIST);
  private final LightTypeParameterListBuilder myTypeParametersList = new LightTypeParameterListBuilder(getManager(), getLanguage());
  private final Collection<PsiMethod> myMethods = ContainerUtil.newArrayList();
  private PsiElement myScope;
  private PsiClass myContainingClass;
  private String myOriginInfo;

  public LightPsiClassBuilder(@NotNull PsiElement context, @NotNull String name) {
    super(context, name);
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  @NotNull
  @Override
  public LightModifierList getModifierList() {
    return myModifierList;
  }

  @NotNull
  @Override
  public LightReferenceListBuilder getExtendsList() {
    return myExtendsList;
  }

  @NotNull
  @Override
  public LightReferenceListBuilder getImplementsList() {
    return myImplementsList;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    // TODO
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethods.toArray(new PsiMethod[myMethods.size()]);
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    // TODO
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getScope() {
    return myScope;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @NotNull
  @Override
  public LightTypeParameterListBuilder getTypeParameterList() {
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
