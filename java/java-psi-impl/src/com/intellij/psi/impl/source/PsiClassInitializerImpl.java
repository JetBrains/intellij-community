// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassInitializerStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PsiClassInitializerImpl extends JavaStubPsiElement<PsiClassInitializerStub> implements PsiClassInitializer {
  public PsiClassInitializerImpl(PsiClassInitializerStub stub) {
    super(stub, JavaStubElementTypes.CLASS_INITIALIZER);
  }

  public PsiClassInitializerImpl(ASTNode node) {
    super(node);
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiCodeBlock getBody(){
    return (PsiCodeBlock)((CompositeElement)getNode()).findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString(){
    return "PsiClassInitializer";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return lastParent == null || PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }

  @Override
  public Icon getElementIcon(int flags) {
    IconManager iconManager = IconManager.getInstance();
    return iconManager.createLayeredIcon(this, iconManager.getPlatformIcon(PlatformIcons.ClassInitializer),
                                         ElementPresentationUtil.getFlags(this, false));
  }
}