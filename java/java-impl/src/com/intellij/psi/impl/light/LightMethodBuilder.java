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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public class LightMethodBuilder extends LightElement implements PsiMethod {
  private final String myName;
  private Computable<PsiType> myReturnType;
  private final LightModifierList myModifierList;
  private LightParameterListBuilder myParameterList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private boolean myConstructor;
  private String myMethodKind = "LightMethodBuilder";

  public LightMethodBuilder(PsiClass constructedClass) {
    this(constructedClass.getManager(), constructedClass.getName());
    setContainingClass(constructedClass);
    myConstructor = true;
  }

  public LightMethodBuilder(PsiManager manager, String name) {
    this(manager, StdLanguages.JAVA, name);
  }
  
  public LightMethodBuilder(PsiManager manager, Language language, String name) {
    super(manager, language);
    myName = name;
    myModifierList = new LightModifierList(manager, language);
    myParameterList = new LightParameterListBuilder(manager, language);
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiTypeParameterList getTypeParameterList() {
    //todo
    return null;
  }

  public PsiDocComment getDocComment() {
    //todo
    return null;
  }

  public boolean isDeprecated() {
    //todo
    return false;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Please don't rename light methods");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public LightMethodBuilder addModifiers(String... modifiers) {
    for (String modifier : modifiers) {
      addModifier(modifier);
    }
    return this;
  }

  public LightMethodBuilder addModifier(String modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  public LightMethodBuilder setModifiers(String... modifiers) {
    myModifierList.clearModifiers();
    addModifiers(modifiers);
    return this;
  }

  public PsiType getReturnType() {
    return myReturnType == null ? null : myReturnType.compute();
  }

  public LightMethodBuilder setReturnType(Computable<PsiType> returnType) {
    myReturnType = returnType;
    return this;
  }

  public LightMethodBuilder setReturnType(final PsiType returnType) {
    return setReturnType(new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        return returnType;
      }
    });
  }

  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  public LightMethodBuilder addParameter(PsiParameter parameter) {
    myParameterList.addParameter(parameter);
    return this;
  }

  public LightMethodBuilder addParameter(String name, String type) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this));
  }
  
  public LightMethodBuilder addParameter(String name, PsiType type) {
    return addParameter(new LightParameter(name, type, this));
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    //todo
    return new LightEmptyImplementsList(getManager());
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return myConstructor;
  }

  public boolean isVarArgs() {
    //todo
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
  }

  public LightMethodBuilder setNavigationElement(PsiElement navigationElement) {
    super.setNavigationElement(navigationElement);
    return this;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public LightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public LightMethodBuilder setMethodKind(String debugKindName) {
    myMethodKind = debugKindName;
    return this;
  }

  public static boolean isLightMethod(PsiMethod method, String kind) {
    return method instanceof LightMethodBuilder && ((LightMethodBuilder)method).myMethodKind.equals(kind);
  }

  public String toString() {
    return myMethodKind + ":" + getName();
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public LightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LightMethodBuilder that = (LightMethodBuilder)o;

    if (myConstructor != that.myConstructor) return false;
    if (myBaseIcon != null ? !myBaseIcon.equals(that.myBaseIcon) : that.myBaseIcon != null) return false;
    if (myContainingClass != null ? !myContainingClass.equals(that.myContainingClass) : that.myContainingClass != null) return false;
    if (!myMethodKind.equals(that.myMethodKind)) return false;
    if (!myModifierList.equals(that.myModifierList)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myParameterList.equals(that.myParameterList)) return false;
    if (myReturnType != null ? !myReturnType.equals(that.myReturnType) : that.myReturnType != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myReturnType != null ? myReturnType.hashCode() : 0);
    result = 31 * result + myModifierList.hashCode();
    result = 31 * result + myParameterList.hashCode();
    result = 31 * result + (myBaseIcon != null ? myBaseIcon.hashCode() : 0);
    result = 31 * result + (myContainingClass != null ? myContainingClass.hashCode() : 0);
    result = 31 * result + (myConstructor ? 1 : 0);
    result = 31 * result + myMethodKind.hashCode();
    return result;
  }
}
