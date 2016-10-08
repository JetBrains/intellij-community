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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsRequiresStatementImpl extends ClsRepositoryPsiElement<PsiRequiresStatementStub> implements PsiRequiresStatement {
  public ClsRequiresStatementImpl(PsiRequiresStatementStub stub) {
    super(stub);
  }

  @Nullable
  @Override
  public PsiJavaModuleReferenceElement getReferenceElement() {
    return null;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    PsiRequiresStatementStub stub = getStub();
    buffer.append("requires ");
    if (stub.isPublic()) buffer.append("public ");
    if (stub.isStatic()) buffer.append("static ");
    buffer.append(stub.getModuleName()).append(";\n");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REQUIRES_STATEMENT);
  }

  @Override
  public String toString() {
    return "PsiRequiresStatement";
  }
}