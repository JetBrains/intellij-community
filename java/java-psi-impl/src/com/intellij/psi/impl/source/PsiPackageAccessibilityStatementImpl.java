// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class PsiPackageAccessibilityStatementImpl extends JavaStubPsiElement<PsiPackageAccessibilityStatementStub> implements PsiPackageAccessibilityStatement {
  public PsiPackageAccessibilityStatementImpl(@NotNull PsiPackageAccessibilityStatementStub stub) {
    super(stub, stub.getStubType());
  }

  public PsiPackageAccessibilityStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull Role getRole() {
    return JavaPackageAccessibilityStatementElementType.typeToRole(getElementType());
  }

  @Override
  public @Nullable PsiJavaCodeReferenceElement getPackageReference() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
  }

  @Override
  public @Nullable String getPackageName() {
    PsiPackageAccessibilityStatementStub stub = getGreenStub();
    if (stub != null) {
      return StringUtil.nullize(stub.getPackageName());
    }
    else {
      PsiJavaCodeReferenceElement ref = getPackageReference();
      return ref != null ? PsiNameHelper.getQualifiedClassName(ref.getText(), true) : null;
    }
  }

  @Override
  public @NotNull Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
    return psiTraverser().children(this).filter(PsiJavaModuleReferenceElement.class);
  }

  @Override
  public @NotNull List<String> getModuleNames() {
    PsiPackageAccessibilityStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getTargets();
    }
    else {
      List<String> targets = new SmartList<>();
      for (PsiJavaModuleReferenceElement refElement : getModuleReferences()) targets.add(refElement.getReferenceText());
      return targets;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageAccessibilityStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackageAccessibilityStatement";
  }
}