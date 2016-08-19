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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

class ClsJavaModuleReferenceElementImpl extends ClsElementImpl implements PsiJavaModuleReferenceElement {
  private final PsiElement myParent;
  private final String myText;
  private final PsiJavaModuleReference myReference;

  public ClsJavaModuleReferenceElementImpl(PsiElement parent, String text) {
    myParent = parent;
    myText = text;
    myReference = myParent instanceof PsiJavaModule ? null : new PsiJavaModuleReference(this);
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
    buffer.append(getReferenceText());
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODULE_REFERENCE);
  }

  @NotNull
  @Override
  public String getReferenceText() {
    return myText;
  }

  @Override
  public PsiPolyVariantReference getReference() {
    return myReference;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModuleReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModuleReference";
  }
}