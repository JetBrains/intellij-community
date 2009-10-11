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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.ArrayElementDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.Value;

public class ArrayElementDescriptorImpl extends ValueDescriptorImpl implements ArrayElementDescriptor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl");

  private final int myIndex;
  private final ArrayReference myArray;

  public ArrayElementDescriptorImpl(Project project, ArrayReference array, int index) {
    super(project);
    myArray = array;
    myIndex = index;
    setLvalue(true);
  }

  public int getIndex() {
    return myIndex;
  }

  public ArrayReference getArray() {
    return myArray;
  }

  public String getName() {
    return String.valueOf(myIndex);
  }

  public String calcValueName() {
    return "[" + getName() + "]";
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    try {
      return myArray.getValue(myIndex);
    }
    catch (ObjectCollectedException e) {
      throw EvaluateExceptionUtil.ARRAY_WAS_COLLECTED;
    }
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText("this[" + myIndex + "]", null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }
}
