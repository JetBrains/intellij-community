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

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import static com.intellij.openapi.util.text.StringUtil.nullize;

public class ClsPackageAccessibilityStatementImpl extends ClsRepositoryPsiElement<PsiPackageAccessibilityStatementStub> implements PsiPackageAccessibilityStatement {
  private final NullableLazyValue<PsiJavaCodeReferenceElement> myPackageReference;
  private final NotNullLazyValue<Iterable<PsiJavaModuleReferenceElement>> myModuleReferences;

  public ClsPackageAccessibilityStatementImpl(PsiPackageAccessibilityStatementStub stub) {
    super(stub);
    myPackageReference = new AtomicNullableLazyValue<PsiJavaCodeReferenceElement>() {
      @Override
      protected PsiJavaCodeReferenceElement compute() {
        String packageName = getPackageName();
        return packageName != null ? new ClsJavaCodeReferenceElementImpl(ClsPackageAccessibilityStatementImpl.this, packageName) : null;
      }
    };
    myModuleReferences = new AtomicNotNullLazyValue<Iterable<PsiJavaModuleReferenceElement>>() {
      @NotNull
      @Override
      protected Iterable<PsiJavaModuleReferenceElement> compute() {
        return ContainerUtil.map(getStub().getTargets(),
                                 target -> new ClsJavaModuleReferenceElementImpl(ClsPackageAccessibilityStatementImpl.this, target));
      }
    };
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
    buffer.append(getRole().toString().toLowerCase(Locale.US)).append(' ').append(getPackageName());
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