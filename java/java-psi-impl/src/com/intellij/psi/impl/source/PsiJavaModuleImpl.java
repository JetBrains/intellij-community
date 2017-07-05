/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
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

  @NotNull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this).filter(PsiRequiresStatement.class);
    }
  }

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getExports() {
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

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getOpens() {
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

  @NotNull
  @Override
  public Iterable<PsiUsesStatement> getUses() {
    return psiTraverser().children(this).filter(PsiUsesStatement.class);
  }

  @NotNull
  @Override
  public Iterable<PsiProvidesStatement> getProvides() {
    return psiTraverser().children(this).filter(PsiProvidesStatement.class);
  }

  @NotNull
  @Override
  public PsiJavaModuleReferenceElement getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiJavaModuleReferenceElement.class);
  }

  @NotNull
  @Override
  public String getName() {
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
    PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(getProject());
    PsiJavaModuleReferenceElement newName = factory.createModuleFromText("module " + name + " {}").getNameIdentifier();
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

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
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

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return getNameIdentifier();
  }

  @Override
  public PsiElement getOriginalElement() {
    return CachedValuesManager.getCachedValue(this, () -> {
      JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(getProject());
      PsiJavaModule result = helper != null ? helper.getOriginalModule(this) : this;
      return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
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
  public String toString() {
    return "PsiJavaModule:" + getName();
  }
}