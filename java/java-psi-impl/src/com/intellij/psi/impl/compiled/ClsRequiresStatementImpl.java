// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaModuleReference;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public final class ClsRequiresStatementImpl extends ClsRepositoryPsiElement<PsiRequiresStatementStub> implements PsiRequiresStatement {
  private final NotNullLazyValue<PsiJavaModuleReferenceElement> myModuleReference;

  public ClsRequiresStatementImpl(PsiRequiresStatementStub stub) {
    super(stub);
    myModuleReference = NotNullLazyValue.atomicLazy(() -> {
      return new ClsJavaModuleReferenceElementImpl(this, getStub().getModuleName());
    });
  }

  @Override
  public PsiJavaModuleReferenceElement getReferenceElement() {
    return myModuleReference.getValue();
  }

  @Override
  public String getModuleName() {
    return getStub().getModuleName();
  }

  @Override
  public PsiJavaModuleReference getModuleReference() {
    return myModuleReference.getValue().getReference();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("requires ");
    appendText(getModifierList(), indentLevel, buffer);
    buffer.append(getModuleName()).append(";\n");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REQUIRES_STATEMENT);
    setMirror(getModifierList(), SourceTreeToPsiMap.<PsiRequiresStatement>treeToPsiNotNull(element).getModifierList());
  }

  @Override
  public PsiModifierList getModifierList() {
    StubElement<PsiModifierList> childStub = getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    return childStub != null ? childStub.getPsi() : null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public String toString() {
    return "PsiRequiresStatement";
  }
}