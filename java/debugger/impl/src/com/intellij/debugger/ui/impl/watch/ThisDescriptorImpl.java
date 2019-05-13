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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

public class ThisDescriptorImpl extends ValueDescriptorImpl{

  public ThisDescriptorImpl(Project project) {
    super(project);
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return evaluationContext != null ? evaluationContext.computeThisObject() : null;
  }

  @Override
  public String getName() {
    //noinspection HardCodedStringLiteral
    return "this";
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    try {
      return elementFactory.createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }

  @Override
  public boolean canSetValue() {
    return false;
  }
}
