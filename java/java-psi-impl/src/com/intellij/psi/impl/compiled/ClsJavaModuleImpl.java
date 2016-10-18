/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class ClsJavaModuleImpl extends ClsRepositoryPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  private PsiJavaModuleReferenceElement myReference;

  public ClsJavaModuleImpl(PsiJavaModuleStub stub) {
    super(stub);
    myReference = new ClsJavaModuleReferenceElementImpl(this, stub.getName());
  }

  @NotNull
  @Override
  public PsiJavaModuleReferenceElement getNameElement() {
    return myReference;
  }

  @NotNull
  @Override
  public String getModuleName() {
    return myReference.getReferenceText();
  }

  @NotNull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
  }

  @NotNull
  @Override
  public Iterable<PsiExportsStatement> getExports() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiExportsStatement.EMPTY_ARRAY));
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append("module ").append(getModuleName()).append(" {\n");

    int newIndentLevel = indentLevel + getIndentSize();

    int position = buffer.length();
    for (PsiRequiresStatement statement : getRequires()) appendText(statement, newIndentLevel, buffer);

    if (buffer.length() > position) buffer.append('\n');
    position = buffer.length();
    for (PsiExportsStatement statement : getExports()) appendText(statement, newIndentLevel, buffer);

    if (buffer.length() > position) buffer.append('\n');
    StringUtil.repeatSymbol(buffer, ' ', newIndentLevel);
    buffer.append("/* ... */\n}");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    PsiJavaModule mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirrorCheckingType(element, JavaElementType.MODULE);
    setMirror(getNameElement(), mirror.getNameElement());

    setMirrors(asList(getStub().getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY)),
               PsiTreeUtil.getChildrenOfTypeAsList(mirror, PsiRequiresStatement.class));

    setMirrors(asList(getStub().getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiExportsStatement.EMPTY_ARRAY)),
               PsiTreeUtil.getChildrenOfTypeAsList(mirror, PsiExportsStatement.class));
  }

  @Override
  public String getName() {
    return getModuleName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
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
    return getNameElement();
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
    return "PsiJavaModule:" + getModuleName();
  }
}