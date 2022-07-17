// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.NotNullLazyValue.atomicLazy;
import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;

public class ClsMethodImpl extends ClsMemberImpl<PsiMethodStub> implements PsiAnnotationMethod {
  private final NotNullLazyValue<PsiTypeElement> myReturnType;
  private final NullableLazyValue<PsiAnnotationMemberValue> myDefaultValue;

  public ClsMethodImpl(final PsiMethodStub stub) {
    super(stub);

    myReturnType = isConstructor() ? null : atomicLazy(() -> new ClsTypeElementImpl(this, getStub().getReturnTypeText()));

    String text = getStub().getDefaultValueText();
    myDefaultValue =
      StringUtil.isEmptyOrSpaces(text) ? null : volatileLazyNullable(() -> ClsParsingUtil.createMemberValueFromText(text, getManager(), this));
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return getChildren(getDocComment(), getModifierList(), getReturnTypeElement(), getNameIdentifier(), getParameterList(),
                       getThrowsList(), getDefaultValue());
  }

  @Override
  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
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
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return myReturnType != null ? myReturnType.getValue() : null;
  }

  @Override
  public PsiType getReturnType() {
    PsiTypeElement typeElement = getReturnTypeElement();
    return typeElement == null ? null : typeElement.getType();
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST)).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiParameterList getParameterList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.PARAMETER_LIST)).getPsi();
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.THROWS_LIST)).getPsi();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return Objects.requireNonNull(getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST)).getPsi();
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return getStub().isDeprecated() || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiAnnotationMemberValue getDefaultValue() {
    return myDefaultValue != null ? myDefaultValue.getValue() : null;
  }

  @Override
  public boolean isConstructor() {
    return getStub().isConstructor();
  }

  @Override
  public boolean isVarArgs() {
    return getStub().isVarArgs();
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);
    appendText(getModifierList(), indentLevel, buffer, "");
    appendText(getTypeParameterList(), indentLevel, buffer, " ");
    if (!isConstructor()) {
      appendText(getReturnTypeElement(), indentLevel, buffer, " ");
    }
    appendText(getNameIdentifier(), indentLevel, buffer, "");
    appendText(getParameterList(), indentLevel, buffer);

    PsiReferenceList throwsList = getThrowsList();
    if (throwsList.getReferencedTypes().length > 0) {
      buffer.append(' ');
      appendText(throwsList, indentLevel, buffer);
    }

    PsiAnnotationMemberValue defaultValue = getDefaultValue();
    if (defaultValue != null) {
      buffer.append(" default ");
      appendText(defaultValue, indentLevel, buffer);
    }

    if (hasModifierProperty(PsiModifier.ABSTRACT) || hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(";");
    }
    else {
      buffer.append(" { /* compiled code */ }");
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiMethod mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirrorIfPresent(getDocComment(), mirror.getDocComment());
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getTypeParameterList(), mirror.getTypeParameterList());
    if (!isConstructor()) {
      setMirror(getReturnTypeElement(), mirror.getReturnTypeElement());
    }
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
    setMirror(getParameterList(), mirror.getParameterList());
    setMirror(getThrowsList(), mirror.getThrowsList());

    PsiAnnotationMemberValue defaultValue = getDefaultValue();
    if (defaultValue != null) {
      assert mirror instanceof PsiAnnotationMethod : this;
      setMirror(defaultValue, ((PsiAnnotationMethod)mirror).getDefaultValue());
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
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
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) return true;

    if (!PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place)) return false;

    final PsiParameter[] parameters = getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (!processor.execute(parameter, state)) return false;
    }

    return true;
  }

  @Nullable
  public PsiMethod getSourceMirrorMethod() {
    return CachedValuesManager.getProjectPsiDependentCache(this, __ -> calcSourceMirrorMethod());
  }

  @Nullable
  private PsiMethod calcSourceMirrorMethod() {
    PsiClass mirrorClass = ((ClsClassImpl)getParent()).getSourceMirrorClass();
    if (mirrorClass != null) {
      for (PsiMethod sourceMethod: mirrorClass.findMethodsByName(getName(), false)) {
        if (MethodSignatureUtil.areParametersErasureEqual(this, sourceMethod)) {
          return sourceMethod;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy navigationPolicy : ClsCustomNavigationPolicy.EP_NAME.getExtensionList()) {
      try {
        PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
        if (navigationElement != null) return navigationElement;
      }
      catch (IndexNotReadyException ignore) { }
    }

    try {
      PsiMethod method = getSourceMirrorMethod();
      if (method != null) return method.getNavigationElement();
    }
    catch (IndexNotReadyException ignore) { }

    return this;
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
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
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

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public String toString() {
    return "PsiMethod:" + getName();
  }
}
