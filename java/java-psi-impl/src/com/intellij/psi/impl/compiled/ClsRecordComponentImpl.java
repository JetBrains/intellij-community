// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordComponentStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClsRecordComponentImpl extends ClsRepositoryPsiElement<PsiRecordComponentStub> implements PsiRecordComponent {
  private final NotNullLazyValue<PsiTypeElement> myType;

  public ClsRecordComponentImpl(@NotNull PsiRecordComponentStub stub) {
    super(stub);
    myType = NotNullLazyValue.atomicLazy(() -> new ClsTypeElementImpl(this, getStub().getType()));
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return getStub().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  @NotNull
  public PsiTypeElement getTypeElement() {
    return myType.getValue();
  }

  @Override
  @NotNull
  public PsiType getType() {
    return getTypeElement().getType();
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    final StubElement<PsiModifierList> child = getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    assert child != null;
    return child.getPsi();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    PsiAnnotation[] annotations = getModifierList().getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      appendText(annotation, indentLevel, buffer);
      buffer.append(' ');
    }
    appendText(getTypeElement(), indentLevel, buffer, " ");
    buffer.append(getName());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiParameter mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getTypeElement(), mirror.getTypeElement());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordComponent(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean isVarArgs() {
    return getStub().isVararg();
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(getParent());
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    PsiClass clsClass = getContainingClass();
    if (clsClass != null) {
      PsiClass psiClass = ObjectUtils.tryCast(clsClass.getNavigationElement(), PsiClass.class);
      if (psiClass != null && psiClass != clsClass) {
        PsiRecordComponent[] clsComponents = clsClass.getRecordComponents();
        int index = ArrayUtil.indexOf(clsComponents, this);
        if (index >= 0) {
          PsiRecordComponent[] psiComponents = psiClass.getRecordComponents();
          if (psiComponents.length == clsComponents.length) {
            return psiComponents[index];
          }
        }
      }
    }
    return this;
  }

  @Override
  public String toString() {
    return "PsiRecordComponent:" + getName();
  }

  @Override
  public @Nullable PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiRecordHeader ? ((PsiRecordHeader)parent).getContainingClass() : null;
  }
}
