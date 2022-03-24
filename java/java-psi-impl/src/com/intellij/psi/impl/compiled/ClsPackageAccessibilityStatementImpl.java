// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.NotNullLazyValue.atomicLazy;
import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;
import static com.intellij.openapi.util.text.StringUtil.nullize;

public final class ClsPackageAccessibilityStatementImpl extends ClsRepositoryPsiElement<PsiPackageAccessibilityStatementStub> implements PsiPackageAccessibilityStatement {
  private final NullableLazyValue<PsiJavaCodeReferenceElement> myPackageReference;
  private final NotNullLazyValue<Iterable<PsiJavaModuleReferenceElement>> myModuleReferences;

  public ClsPackageAccessibilityStatementImpl(PsiPackageAccessibilityStatementStub stub) {
    super(stub);
    myPackageReference = atomicLazyNullable(() -> {
        String packageName = getPackageName();
        return packageName != null ? new ClsJavaCodeReferenceElementImpl(this, packageName) : null;
    });
    myModuleReferences = atomicLazy(() -> ContainerUtil.map(getStub().getTargets(), target -> new ClsJavaModuleReferenceElementImpl(this, target)));
  }

  @NotNull
  @Override
  public Role getRole() {
    return JavaPackageAccessibilityStatementElementType.typeToRole(getStub().getStubType());
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return myPackageReference.getValue();
  }

  @Override
  public String getPackageName() {
    return nullize(getStub().getPackageName());
  }

  @NotNull
  @Override
  public Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
    return myModuleReferences.getValue();
  }

  @NotNull
  @Override
  public List<String> getModuleNames() {
    return getStub().getTargets();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append(StringUtil.toLowerCase(getRole().toString())).append(' ').append(getPackageName());
    List<String> targets = getStub().getTargets();
    if (!targets.isEmpty()) {
      buffer.append(" to ");
      for (int i = 0; i < targets.size(); i++) {
        if (i > 0) buffer.append(", ");
        buffer.append(targets.get(i));
      }
    }
    buffer.append(";\n");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, getStub().getStubType());
  }

  @Override
  public String toString() {
    return "PsiPackageAccessibilityStatement[" + getRole() + "]";
  }
}
