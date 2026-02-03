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
package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;

public interface ClassReferenceVisitor {
  class TypeOccurence {
    public TypeOccurence(PsiElement element, PsiType outermostType) {
      this.element = element;
      this.outermostType = outermostType;
    }

    public final PsiElement element;
    public final PsiType outermostType;
  }

  void visitReferenceExpression(PsiReferenceExpression referenceExpression);

  void visitLocalVariableDeclaration(PsiLocalVariable variable, TypeOccurence occurence);
  void visitFieldDeclaration(PsiField field, TypeOccurence occurence);
  void visitParameterDeclaration(PsiParameter parameter, TypeOccurence occurence);
  void visitMethodReturnType(PsiMethod method, TypeOccurence occurence);
  void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, TypeOccurence occurence);

  void visitNewExpression(PsiNewExpression newExpression, TypeOccurence occurence);

  void visitOther(PsiElement ref);
}
