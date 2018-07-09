// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class ClsFieldImpl extends ClsMemberImpl<PsiFieldStub> implements PsiField, PsiVariableEx, ClsModifierListOwner {
  private final NotNullLazyValue<PsiTypeElement> myTypeElement;
  private final NullableLazyValue<PsiExpression> myInitializer;

  public ClsFieldImpl(@NotNull PsiFieldStub stub) {
    super(stub);
    myTypeElement = new AtomicNotNullLazyValue<PsiTypeElement>() {
      @NotNull
      @Override
      protected PsiTypeElement compute() {
        PsiFieldStub stub = getStub();
        String typeText = TypeInfo.createTypeText(stub.getType(false));
        assert typeText != null : stub;
        return new ClsTypeElementImpl(ClsFieldImpl.this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
      }
    };
    myInitializer = new VolatileNullableLazyValue<PsiExpression>() {
      @Nullable
      @Override
      protected PsiExpression compute() {
        String initializerText = getStub().getInitializerText();
        return initializerText != null && !Comparing.equal(PsiFieldStub.INITIALIZER_TOO_LONG, initializerText) ?
               ClsParsingUtil.createExpressionFromText(initializerText, getManager(), ClsFieldImpl.this) : null;
      }
    };
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return getChildren(getDocComment(), getModifierList(), getTypeElement(), getNameIdentifier());
  }

  @Override
  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  @Override
  @NotNull
  public PsiType getType() {
    return assertNotNull(getTypeElement()).getType();
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return myTypeElement.getValue();
  }

  @Override
  public PsiModifierList getModifierList() {
    return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST)).getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return assertNotNull(getModifierList()).hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return myInitializer.getValue();
  }

  @Override
  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  @Override
  public Object computeConstantValue() {
    return computeConstantValue(new THashSet<>());
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;

    PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      String qName = containingClass.getQualifiedName();
      if ("java.lang.Float".equals(qName)) {
        String name = getName();
        if ("POSITIVE_INFINITY".equals(name)) return Float.POSITIVE_INFINITY;
        if ("NEGATIVE_INFINITY".equals(name)) return Float.NEGATIVE_INFINITY;
        if ("NaN".equals(name)) return Float.NaN;
      }
      else if ("java.lang.Double".equals(qName)) {
        String name = getName();
        if ("POSITIVE_INFINITY".equals(name)) return Double.POSITIVE_INFINITY;
        if ("NEGATIVE_INFINITY".equals(name)) return Double.NEGATIVE_INFINITY;
        if ("NaN".equals(name)) return Double.NaN;
      }
    }

    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  @Override
  public boolean isDeprecated() {
    return getStub().isDeprecated() || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException { }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);
    appendText(getModifierList(), indentLevel, buffer, "");
    appendText(getTypeElement(), indentLevel, buffer, " ");
    appendText(getNameIdentifier(), indentLevel, buffer);

    PsiExpression initializer = getInitializer();
    if (initializer != null) {
      buffer.append(" = ");
      buffer.append(initializer.getText());
    }

    buffer.append(';');
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiField mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getDocComment(), mirror.getDocComment());
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getTypeElement(), mirror.getTypeElement());
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitField(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy navigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      try {
        PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
        if (navigationElement != null) return navigationElement;
      }
      catch (IndexNotReadyException ignore) { }
    }

    try {
      PsiClass mirrorClass = ((ClsClassImpl)getParent()).getSourceMirrorClass();
      if (mirrorClass != null) {
        PsiElement field = mirrorClass.findFieldByName(getName(), false);
        if (field != null) return field.getNavigationElement();
      }
    }
    catch (IndexNotReadyException ignore) { }

    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(PlatformIcons.FIELD_ICON, this, false);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
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
    return "PsiField:" + getName();
  }
}