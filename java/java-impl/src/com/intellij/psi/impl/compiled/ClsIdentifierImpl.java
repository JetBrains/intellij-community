/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class ClsIdentifierImpl extends ClsElementImpl implements PsiIdentifier, PsiJavaToken {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsIdentifierImpl");

  private final PsiElement myParent;
  private final String myText;

  ClsIdentifierImpl(PsiElement parent, String text) {
    myParent = parent;
    myText = text;
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public String getText() {
    return myText;
  }

  @NotNull
  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent(){
    return myParent;
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer){
    buffer.append(getText());
  }

  public void setMirror(@NotNull TreeElement element){
    setMirrorCheckingType(element, ElementType.IDENTIFIER);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIdentifier(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiIdentifier:" + getText();
  }
}
