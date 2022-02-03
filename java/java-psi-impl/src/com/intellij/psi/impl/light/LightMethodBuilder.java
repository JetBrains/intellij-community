// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class LightMethodBuilder extends LightElement implements PsiMethod, OriginInfoAwareElement {
  private final String myName;
  private Supplier<? extends PsiType> myReturnType;
  private final PsiModifierList myModifierList;
  private final PsiParameterList myParameterList;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiReferenceList myThrowsList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private boolean myConstructor;
  private String myMethodKind = "LightMethodBuilder";
  private String myOriginInfo;

  public LightMethodBuilder(PsiClass constructedClass, Language language) {
    this(constructedClass.getManager(), language, constructedClass.getName());
    setContainingClass(constructedClass);
  }

  public LightMethodBuilder(PsiManager manager, @NlsSafe String name) {
    this(manager, JavaLanguage.INSTANCE, name);
  }

  public LightMethodBuilder(PsiManager manager, Language language, @NlsSafe String name) {
    this(manager, language, name, new LightParameterListBuilder(manager, language), new LightModifierList(manager, language));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            @NlsSafe String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList) {
    this(manager, language, name, parameterList, modifierList,
         new LightReferenceListBuilder(manager, language, PsiReferenceList.Role.THROWS_LIST),
         new LightTypeParameterListBuilder(manager, language));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            @NlsSafe @NotNull String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList,
                            PsiReferenceList throwsList,
                            PsiTypeParameterList typeParameterList) {
    super(manager, language);
    myName = name;
    myParameterList = parameterList;
    myModifierList = modifierList;
    myThrowsList = throwsList;
    myTypeParameterList = typeParameterList;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myTypeParameterList;
  }

  @Override
  public PsiDocComment getDocComment() {
    //todo
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final String className = myContainingClass == null ? "null" : myContainingClass.getQualifiedName();
    throw new UnsupportedOperationException("Please don't rename light methods: writable=" + isWritable() +
                                            "; class=" + getClass() +
                                            "; name=" + getName() +
                                            "; inClass=" + className);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
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

  public LightMethodBuilder addModifier(@NlsSafe String modifier) {
    ((LightModifierList)myModifierList).addModifier(modifier);
    return this;
  }

  public LightMethodBuilder setModifiers(@NlsSafe String... modifiers) {
    ((LightModifierList)myModifierList).clearModifiers();
    addModifiers(modifiers);
    return this;
  }

  @Override
  public PsiType getReturnType() {
    return myReturnType == null ? null : myReturnType.get();
  }

  public LightMethodBuilder setMethodReturnType(Computable<? extends PsiType> returnType) {
    myReturnType = () -> returnType.compute();
    return this;
  }

  public LightMethodBuilder setMethodReturnType(Supplier<? extends PsiType> returnType) {
    myReturnType = returnType;
    return this;
  }

  public LightMethodBuilder setMethodReturnType(PsiType returnType) {
    return setMethodReturnType(new Computable.PredefinedValueComputable<>(returnType));
  }

  public LightMethodBuilder setMethodReturnType(@NlsSafe @NotNull final String returnType) {
    return setMethodReturnType(NotNullLazyValue.lazy(
      () -> JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(returnType, getResolveScope()))::getValue);
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @Override
  @NotNull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  public LightMethodBuilder addParameter(@NotNull PsiParameter parameter) {
    ((LightParameterListBuilder)myParameterList).addParameter(parameter);
    return this;
  }

  public LightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NlsSafe @NotNull String type) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this));
  }

  public LightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NotNull PsiType type) {
    return addParameter(new LightParameter(name, type, this, JavaLanguage.INSTANCE));
  }

  public LightMethodBuilder addParameter(@NlsSafe @NotNull String name, @NotNull PsiType type, boolean isVarArgs) {
    if (isVarArgs && !(type instanceof PsiEllipsisType)) {
      type = new PsiEllipsisType(type);
    }
    return addParameter(new LightParameter(name, type, this, JavaLanguage.INSTANCE, isVarArgs));
  }

  public LightMethodBuilder addException(PsiClassType type) {
    ((LightReferenceListBuilder)myThrowsList).addReference(type);
    return this;
  }

  public LightMethodBuilder addException(@NlsSafe String fqName) {
    ((LightReferenceListBuilder)myThrowsList).addReference(fqName);
    return this;
  }


  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  public LightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    return this;
  }

  @Override
  public boolean isConstructor() {
    return myConstructor;
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(this);
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  public PsiMethod @NotNull [] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
  }

  @Override
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

  @Override
  public String toString() {
    return myMethodKind + ":" + getName();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  public LightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Nullable
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LightMethodBuilder that = (LightMethodBuilder)o;

    if (myConstructor != that.myConstructor) return false;
    if (!Objects.equals(myContainingClass, that.myContainingClass)) return false;
    if (!myMethodKind.equals(that.myMethodKind)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!getParameterTypes().equals(that.getParameterTypes())) return false;
    if (!Objects.equals(getReturnType(), that.getReturnType())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, getReturnType(), myConstructor, myMethodKind, myContainingClass, getParameterTypes());
  }

  @NotNull
  private List<PsiType> getParameterTypes() {
    return ContainerUtil.map(getParameterList().getParameters(), PsiParameter::getType);
  }

  public LightMethodBuilder addTypeParameter(PsiTypeParameter parameter) {
    ((LightTypeParameterListBuilder)myTypeParameterList).addParameter(new LightTypeParameter(parameter));
    return this;
  }

  @Nullable
  @Override
  @NonNls
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@Nullable @NonNls String originInfo) {
    myOriginInfo = originInfo;
  }

}
