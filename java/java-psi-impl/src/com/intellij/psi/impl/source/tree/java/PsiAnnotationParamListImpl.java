/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationParameterListStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class PsiAnnotationParamListImpl extends JavaStubPsiElement<PsiAnnotationParameterListStub> implements PsiAnnotationParameterList {
  public PsiAnnotationParamListImpl(@NotNull PsiAnnotationParameterListStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION_PARAMETER_LIST);
  }

  public PsiAnnotationParamListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiNameValuePair @NotNull [] getAttributes() {
    return getStubOrPsiChildren(JavaStubElementTypes.NAME_VALUE_PAIR, PsiNameValuePair.ARRAY_FACTORY);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiAnnotationParameterList";
  }
}
