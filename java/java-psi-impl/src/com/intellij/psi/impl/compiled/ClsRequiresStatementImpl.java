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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class ClsRequiresStatementImpl extends ClsRepositoryPsiElement<PsiRequiresStatementStub> implements PsiRequiresStatement {
  private final NotNullLazyValue<PsiJavaModuleReferenceElement> myModuleReference;

  public ClsRequiresStatementImpl(PsiRequiresStatementStub stub) {
    super(stub);
    myModuleReference = new AtomicNotNullLazyValue<PsiJavaModuleReferenceElement>() {
      @NotNull
      @Override
      protected PsiJavaModuleReferenceElement compute() {
        return new ClsJavaModuleReferenceElementImpl(ClsRequiresStatementImpl.this, getStub().getModuleName());
      }
    };
  }

  @Override
  public PsiJavaModuleReferenceElement getReferenceElement() {
    return myModuleReference.getValue();
  }

  @Override
  public String getModuleName() {
    return getStub().getModuleName();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("requires ");
    appendText(getModifierList(), indentLevel, buffer);
    buffer.append(getModuleName()).append(";\n");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REQUIRES_STATEMENT);
    setMirror(getModifierList(), SourceTreeToPsiMap.<PsiRequiresStatement>treeToPsiNotNull(element).getModifierList());
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

  @Override
  public String toString() {
    return "PsiRequiresStatement";
  }
}