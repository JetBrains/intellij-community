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
package com.intellij.refactoring.typeCook.deductive.util;

import com.intellij.psi.*;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author db
 */
public class VictimCollector extends Visitor {
  final Set<PsiElement> myVictims = new LinkedHashSet<>();
  final PsiElement[] myElements;
  final Settings mySettings;

  public VictimCollector(final PsiElement[] elements, final Settings settings) {
    myElements = elements;
    mySettings = settings;
  }

  private void testNAdd(final PsiElement element, final PsiType t) {
    if (Util.isRaw(t, mySettings)) {
      if (element instanceof PsiNewExpression && t.getCanonicalText().equals(CommonClassNames.JAVA_LANG_OBJECT)){
        return;  
      }

      myVictims.add(element);
    }
  }

  @Override public void visitLocalVariable(final PsiLocalVariable variable) {
    testNAdd(variable, variable.getType());

    super.visitLocalVariable(variable);
  }

  @Override public void visitForeachStatement(final PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    testNAdd(parameter, parameter.getType());
  }

  @Override public void visitField(final PsiField field) {
    testNAdd(field, field.getType());

    super.visitField(field);
  }

  @Override public void visitMethod(final PsiMethod method) {
    final PsiParameter[] parms = method.getParameterList().getParameters();

    for (PsiParameter parm : parms) {
      testNAdd(parm, parm.getType());
    }

    if (Util.isRaw(method.getReturnType(), mySettings)) {
      myVictims.add(method);
    }

    final PsiCodeBlock body = method.getBody();

    if (body != null) {
      body.accept(this);
    }
  }

  @Override public void visitNewExpression(final PsiNewExpression expression) {
    if (expression.getClassReference() != null) {
      testNAdd(expression, expression.getType());
    }

    super.visitNewExpression(expression);
  }

  @Override public void visitTypeCastExpression (final PsiTypeCastExpression cast){
    final PsiTypeElement typeElement = cast.getCastType();
    if (typeElement != null) {
      testNAdd(cast, typeElement.getType());
    }

    super.visitTypeCastExpression(cast);
  }

  @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
  }

  @Override public void visitFile(PsiFile file) {
    if (file instanceof PsiJavaFile) {
      super.visitFile(file);
    }
  }

  public Set<PsiElement> getVictims() {
    for (PsiElement element : myElements) {
      element.accept(this);
    }

    return myVictims;
  }
}
