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
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

public class ClsJavaModuleImpl extends ClsRepositoryPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  private final PsiJavaModuleReferenceElement myReference;

  public ClsJavaModuleImpl(PsiJavaModuleStub stub) {
    super(stub);
    myReference = new ClsJavaModuleReferenceElementImpl(this, stub.getName());
  }

  @NotNull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
  }

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getExports() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @NotNull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getOpens() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.OPENS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @NotNull
  @Override
  public Iterable<PsiUsesStatement> getUses() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.USES_STATEMENT, PsiUsesStatement.EMPTY_ARRAY));
  }

  @NotNull
  @Override
  public Iterable<PsiProvidesStatement> getProvides() {
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
    List<PsiElement> statements = newArrayList(children);
    if (!statements.isEmpty()) {
      if (buffer.length() > start) buffer.append('\n');
      for (PsiElement statement : statements) appendText(statement, indentLevel, buffer);
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
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

  private static <T extends PsiElement> void setMirrors(Iterable<T> stubs, Iterable<T> mirrors) {
    setMirrors(newArrayList(stubs), newArrayList(mirrors));
  }

  @NotNull
  @Override
  public PsiJavaModuleReferenceElement getNameIdentifier() {
    return myReference;
  }

  @NotNull
  @Override
  public String getName() {
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

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
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

    return getNameIdentifier();
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