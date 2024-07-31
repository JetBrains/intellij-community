// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class PsiJavaModuleImpl extends JavaStubPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  public PsiJavaModuleImpl(@NotNull PsiJavaModuleStub stub) {
    super(stub, JavaStubElementTypes.MODULE);
  }

  public PsiJavaModuleImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull Iterable<PsiRequiresStatement> getRequires() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this).filter(PsiRequiresStatement.class);
    }
  }

  @Override
  public @NotNull Iterable<PsiPackageAccessibilityStatement> getExports() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this)
        .filter(PsiPackageAccessibilityStatement.class)
        .filter(statement -> statement.getRole() == PsiPackageAccessibilityStatement.Role.EXPORTS);
    }
  }

  @Override
  public @NotNull Iterable<PsiPackageAccessibilityStatement> getOpens() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.OPENS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this)
        .filter(PsiPackageAccessibilityStatement.class)
        .filter(statement -> statement.getRole() == PsiPackageAccessibilityStatement.Role.OPENS);
    }
  }

  @Override
  public @NotNull Iterable<PsiUsesStatement> getUses() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.USES_STATEMENT, PsiUsesStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this).filter(PsiUsesStatement.class);
    }
  }

  @Override
  public @NotNull Iterable<PsiProvidesStatement> getProvides() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.PROVIDES_STATEMENT, PsiProvidesStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this).filter(PsiProvidesStatement.class);
    }
  }

  @Override
  public @NotNull PsiJavaModuleReferenceElement getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiJavaModuleReferenceElement.class);
  }

  @Override
  public @NotNull String getName() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      return getNameIdentifier().getReferenceText();
    }
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiJavaModuleReferenceElement newName = PsiElementFactory.getInstance(getProject()).createModuleReferenceFromText(name, null);
    getNameIdentifier().replace(newName);
    return this;
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public @Nullable PsiDocComment getDocComment() {
    return PsiTreeUtil.getChildOfType(this, PsiDocComment.class);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return JavaResolveUtil.processJavaModuleExports(this, processor, state, lastParent, place);
  }

  @Override
  public PsiElement getOriginalElement() {
    return CachedValuesManager.getCachedValue(this, () -> {
      JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(getProject());
      PsiJavaModule result = helper != null ? helper.getOriginalModule(this) : this;
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
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
  public @NotNull SearchScope getUseScope() {
    return ProjectScope.getProjectScope(getProject());
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getName();
  }
}