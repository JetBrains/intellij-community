/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.*;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ClsMethodImpl extends ClsMemberImpl<PsiMethodStub> implements PsiAnnotationMethod {
  private final NotNullLazyValue<PsiTypeElement> myReturnType;
  private final NotNullLazyValue<PsiAnnotationMemberValue> myDefaultValue;

  public ClsMethodImpl(final PsiMethodStub stub) {
    super(stub);

    myReturnType = isConstructor() ? null : new AtomicNotNullLazyValue<PsiTypeElement>() {
      @NotNull
      @Override
      protected PsiTypeElement compute() {
        PsiMethodStub stub = getStub();
        String typeText = TypeInfo.createTypeText(stub.getReturnTypeText(false));
        assert typeText != null : stub;
        return new ClsTypeElementImpl(ClsMethodImpl.this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
      }
    };

    final String text = getStub().getDefaultValueText();
    myDefaultValue = StringUtil.isEmptyOrSpaces(text) ? null : new AtomicNotNullLazyValue<PsiAnnotationMemberValue>() {
      @NotNull
      @Override
      protected PsiAnnotationMemberValue compute() {
        return ClsParsingUtil.createMemberValueFromText(text, getManager(), ClsMethodImpl.this);
      }
    };
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return getChildren(getDocComment(), getModifierList(), getReturnTypeElement(), getNameIdentifier(), getParameterList(),
                       getThrowsList(), getDefaultValue());
  }

  @Override
  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
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
  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
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
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiParameterList getParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.PARAMETER_LIST).getPsi();
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.THROWS_LIST).getPsi();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return getStub().isDeprecated();
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
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiMethod>() {
      @Nullable
      @Override
      public Result<PsiMethod> compute() {
        return Result.create(calcSourceMirrorMethod(),
                             getContainingFile(),
                             getContainingFile().getNavigationElement(),
                             FileIndexFacade.getInstance(getProject()).getRootModificationTracker());
      }
    });
  }

  @Nullable
  private PsiMethod calcSourceMirrorMethod() {
    PsiClass sourceClassMirror = ((ClsClassImpl)getParent()).getSourceMirrorClass();
    if (sourceClassMirror == null) return null;
    for (PsiMethod sourceMethod : sourceClassMirror.findMethodsByName(getName(), false)) {
      if (MethodSignatureUtil.areParametersErasureEqual(this, sourceMethod)) {
        return sourceMethod;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      PsiElement navigationElement = customNavigationPolicy.getNavigationElement(this);
      if (navigationElement != null) {
        return navigationElement;
      }
    }

    final PsiMethod method = getSourceMirrorMethod();
    return method != null ? method.getNavigationElement() : this;
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(methodIcon, this, false);
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
