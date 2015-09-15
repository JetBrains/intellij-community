/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

import java.util.Collection;

public class ArgumentValueDescriptorImpl extends ValueDescriptorImpl{
  private final int myIndex;
  private final Value myValue;
  private String myName;
  private final boolean myIsParam;

  public ArgumentValueDescriptorImpl(Project project, int index, Value value, boolean isParam) {
    super(project);
    myIndex = index;
    myValue = value;
    myName = getDefaultName();
    myIsParam = isParam;
    setLvalue(true);
  }

  @Override
  public boolean canSetValue() {
    return false;
  }

  public boolean isPrimitive() {
    return myValue instanceof PrimitiveValue;
  }

  public Value calcValue(final EvaluationContextImpl evaluationContext) throws EvaluateException {
    Collection<String> names = LocalVariablesUtil.calcNames(evaluationContext, myIndex);
    String nameString = StringUtil.join(names, " | ");
    if (myIsParam && names.size() == 1) {
      myName = nameString;
    }
    else if (!names.isEmpty()) {
      myName = nameString + ": " + getDefaultName();
    }
    return myValue;
  }

  private String getDefaultName() {
    return DecompiledLocalVariable.getDefaultName(myIndex, myIsParam);
  }

  public String getName() {
    return myName;
  }

  public boolean isParameter() {
    return myIsParam;
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
    }
  }
}