// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClsJavaModuleImpl extends ClsRepositoryPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  private final PsiJavaModuleReferenceElement myReference;

  public ClsJavaModuleImpl(PsiJavaModuleStub stub) {
    super(stub);
    myReference = new ClsJavaModuleReferenceElementImpl(this, stub.getName());
  }

  @Override
  public @NotNull Iterable<PsiRequiresStatement> getRequires() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
  }

  @Override
  public @NotNull Iterable<PsiPackageAccessibilityStatement> getExports() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @Override
  public @NotNull Iterable<PsiPackageAccessibilityStatement> getOpens() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.OPENS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @Override
  public @NotNull Iterable<PsiUsesStatement> getUses() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.USES_STATEMENT, PsiUsesStatement.EMPTY_ARRAY));
  }

  @Override
  public @NotNull Iterable<PsiProvidesStatement> getProvides() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.PROVIDES_STATEMENT, PsiProvidesStatement.EMPTY_ARRAY));
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    appendText(getModifierList(), indentLevel, buffer);
    buffer.append("module ").append(getName()).append(" {\n");

    int newIndentLevel = indentLevel + getIndentSize(), start = buffer.length();
    appendChildren(getRequires(), buffer, newIndentLevel, start);
    appendChildren(getExports(), buffer, newIndentLevel, start);
    appendChildren(getOpens(), buffer, newIndentLevel, start);
    appendChildren(getUses(), buffer, newIndentLevel, start);
    appendChildren(getProvides(), buffer, newIndentLevel, start);

    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append('}');
  }

  private static void appendChildren(Iterable<? extends PsiElement> children, StringBuilder buffer, int indentLevel, int start) {
    List<PsiElement> statements = ContainerUtil.newArrayList(children);
    if (!statements.isEmpty()) {
      if (buffer.length() > start) buffer.append('\n');
      for (PsiElement statement : statements) appendText(statement, indentLevel, buffer);
    }
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODULE);

    PsiJavaModule mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
    setMirror(getModifierList(), mirror.getModifierList());

    setMirrors(getRequires(), mirror.getRequires());
    setMirrors(getExports(), mirror.getExports());
    setMirrors(getOpens(), mirror.getOpens());
    setMirrors(getUses(), mirror.getUses());
    setMirrors(getProvides(), mirror.getProvides());
  }

  private static <T extends PsiElement> void setMirrors(Iterable<? extends T> stubs, Iterable<? extends T> mirrors) {
    setMirrors(ContainerUtil.newArrayList(stubs), ContainerUtil.newArrayList(mirrors));
  }

  @Override
  public @NotNull PsiJavaModuleReferenceElement getNameIdentifier() {
    return myReference;
  }

  @Override
  public @NotNull String getName() {
    return myReference.getReferenceText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
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
  public @Nullable PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    PsiElement parent = getParent();
    if (parent instanceof PsiClassOwner) {
      PsiElement file = parent.getNavigationElement();
      if (file != parent && file instanceof PsiJavaFile) {
        PsiJavaModule module = ((PsiJavaFile)file).getModuleDeclaration();
        if (module != null) {
          return module;
        }
      }
    }

    return this;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModule(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return JavaResolveUtil.processJavaModuleExports(this, processor, state, lastParent, place);
  }
}
