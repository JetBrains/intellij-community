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
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

/**
 *  @author dsl
 */
public class LightReferenceParameterList extends LightElement implements PsiReferenceParameterList {
  private final PsiTypeElement[] myTypeElements;
  private final String myText;

  public LightReferenceParameterList(PsiManager manager, PsiTypeElement[] referenceElements) {
    super(manager, JavaLanguage.INSTANCE);
    myTypeElements = referenceElements;
    myText = calculateText();
  }

  private String calculateText() {
    if (myTypeElements.length == 0) return "";
    final StringJoiner buffer = new StringJoiner(",", "<", ">");
    for (PsiTypeElement type : myTypeElements) {
      buffer.add(type.getText());
    }
    return buffer.toString();
  }

  @Override
  public String toString() {
    return "PsiReferenceParameterList";
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    final PsiTypeElement[] elements = new PsiTypeElement[myTypeElements.length];
    for (int i = 0; i < myTypeElements.length; i++) {
      PsiTypeElement typeElement = myTypeElements[i];
      elements[i] = (PsiTypeElement) typeElement.copy();
    }
    return new LightReferenceParameterList(myManager, elements);
  }

  @Override
  @NotNull
  public PsiTypeElement[] getTypeParameterElements() {
    return myTypeElements;
  }

  @Override
  @NotNull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByTypeElements(myTypeElements);
  }
}
