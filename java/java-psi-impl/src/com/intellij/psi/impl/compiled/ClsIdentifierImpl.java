/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class ClsIdentifierImpl extends ClsElementImpl implements PsiIdentifier, PsiJavaToken {
  private final PsiElement myParent;
  private final String myText;

  ClsIdentifierImpl(@NotNull PsiElement parent, String text) {
    myParent = parent;
    myText = text;
  }

  @Override
  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  private boolean isCorrectName(String name) {
    return name != null && ClsParsingUtil.isJavaIdentifier(name, ((PsiJavaFile)getContainingFile()).getLanguageLevel());
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    String original = getText();
    if (isCorrectName(original)) {
      buffer.append(original);
    }
    else {
      buffer.append("$$").append(original).append(" /* Real name is '").append(original).append("' */");
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaTokenType.IDENTIFIER);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIdentifier(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiIdentifier:" + getText();
  }
}
