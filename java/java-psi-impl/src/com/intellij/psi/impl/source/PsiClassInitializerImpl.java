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
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiClassInitializerImpl extends JavaStubPsiElement<PsiClassInitializerStub> implements PsiClassInitializer {
  public PsiClassInitializerImpl(final PsiClassInitializerStub stub) {
    super(stub, JavaStubElementTypes.CLASS_INITIALIZER);
  }

  public PsiClassInitializerImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiCodeBlock getBody(){
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
    return ElementPresentationUtil.createLayeredIcon(PlatformIcons.CLASS_INITIALIZER, this, false);
  }
}