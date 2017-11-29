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
package com.intellij.java.propertyBased;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.FilePsiMutation;

class AddNullArgument extends FilePsiMutation {

  AddNullArgument(PsiFile file) {
    super(file);
  }

  @Override
  protected void performMutation() {
    PsiTreeUtil.findChildrenOfType(getFile(), PsiMethodCallExpression.class).stream()
      .filter(PsiElement::isValid)
      .filter(call -> call.getArgumentList().getExpressions().length > 1)
      .forEach(call -> call.getArgumentList().add(
        JavaPsiFacade.getElementFactory(getFile().getProject()).createExpressionFromText("null", call)));
  }
}
