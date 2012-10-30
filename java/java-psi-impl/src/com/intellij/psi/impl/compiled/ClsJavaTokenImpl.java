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

public class ClsJavaTokenImpl extends ClsElementImpl implements PsiJavaToken {
  private ClsElementImpl myParent;
  private final IElementType myTokenType;
  private final String myTokenText;

  public ClsJavaTokenImpl(ClsElementImpl parent, IElementType tokenType, String tokenText) {
    myParent = parent;
    myTokenType = tokenType;
    myTokenText = tokenText;
  }

  void setParent(ClsElementImpl parent) {
    myParent = parent;
  }

  @Override
  public IElementType getTokenType() {
    return myTokenType;
  }

  @Override
  public String getText() {
    return myTokenText;
  }

  @NotNull
  @Override
  public PsiElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(getText());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, myTokenType);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitJavaToken(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
