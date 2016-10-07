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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
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
  public PsiJavaModuleReferenceElement getNameElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiJavaModuleReferenceElement.class);
  }

  @NotNull
  @Override
  public String getModuleName() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      return getNameElement().getReferenceText();
    }
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
  public Iterable<PsiExportsStatement> getExports() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiExportsStatement.EMPTY_ARRAY));
    }
    else {
      return psiTraverser().children(this).filter(PsiExportsStatement.class);
    }
  }

  @Override
  public String getName() {
    return getModuleName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(getProject());
    PsiJavaModuleReferenceElement newName = factory.createModuleFromText("module " + name + " {}").getNameElement();
    getNameElement().replace(newName);
    return this;
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