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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiExportsStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class PsiExportsStatementImpl extends JavaStubPsiElement<PsiExportsStatementStub> implements PsiExportsStatement {
  public PsiExportsStatementImpl(@NotNull PsiExportsStatementStub stub) {
    super(stub, JavaStubElementTypes.EXPORTS_STATEMENT);
  }

  public PsiExportsStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
  }

  @Nullable
  @Override
  public String getPackageName() {
    PsiExportsStatementStub stub = getGreenStub();
    if (stub != null) {
      return StringUtil.nullize(stub.getPackageName());
    }
    else {
      PsiJavaCodeReferenceElement ref = getPackageReference();
      return ref != null ? PsiNameHelper.getQualifiedClassName(ref.getText(), true) : null;
    }
  }

  @NotNull
  @Override
  public Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
    return psiTraverser().children(this).filter(PsiJavaModuleReferenceElement.class);
  }

  @NotNull
  @Override
  public List<String> getModuleNames() {
    PsiExportsStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getTargets();
    }
    else {
      List<String> targets = ContainerUtil.newSmartList();
      for (PsiJavaModuleReferenceElement refElement : getModuleReferences()) targets.add(refElement.getReferenceText());
      return targets;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExportsStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiExportsStatement";
  }
}