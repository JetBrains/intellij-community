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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

import java.util.Stack;

public class ArgumentValueDescriptorImpl extends ValueDescriptorImpl{
  private final int myIndex;
  private final Value myValue;
  private String myName;
  private boolean myParameterNameCalcutated;
  private final String myDefaultName;

  public ArgumentValueDescriptorImpl(Project project, int index, Value value, String name) {
    super(project);
    myIndex = index;
    myValue = value;
    myDefaultName = name != null ? name : "arg" + String.valueOf(index);
    myName = myDefaultName;
    setLvalue(true);
  }

  public boolean isPrimitive() {
    return myValue instanceof PrimitiveValue;
  }

  public Value calcValue(final EvaluationContextImpl evaluationContext) throws EvaluateException {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
        if (position != null) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(position.getElementAt(), PsiMethod.class);
          if (method != null) {
            final PsiParameterList params = method.getParameterList();
            if (myIndex < params.getParametersCount()) {
              final PsiParameter param = params.getParameters()[myIndex];
              myName = param.getName();
              myParameterNameCalcutated = true;
            }
            else {
              // treat myIndex as a variable slot index
              final PsiCodeBlock body = method.getBody();
              if (body != null) {
                final StringBuilder nameBuilder = new StringBuilder();
                nameBuilder.append(myDefaultName);
                try {
                  final int startSlot = params.getParametersCount() + (method.hasModifierProperty(PsiModifier.STATIC)? 0 : 1);
                  body.accept(new JavaRecursiveElementVisitor() {
                    private int myCurrentSlotIndex = startSlot;
                    private final Stack<Integer> myIndexStack = new Stack<Integer>();
                    @Override
                    public void visitCodeBlock(PsiCodeBlock block) {
                      myIndexStack.push(myCurrentSlotIndex);
                      try {
                        super.visitCodeBlock(block);
                      }
                      finally {
                        myCurrentSlotIndex = myIndexStack.pop();
                      }
                    }

                    @Override
                    public void visitLocalVariable(PsiLocalVariable variable) {
                      if (myCurrentSlotIndex == myIndex) {
                        if (nameBuilder.length() == myDefaultName.length()) {
                          nameBuilder.append(": ");
                        }
                        else {
                          nameBuilder.append("|");
                        }
                        nameBuilder.append(variable.getName());
                      }
                      final PsiType varType = variable.getType();
                      myCurrentSlotIndex += (varType == PsiType.DOUBLE || varType == PsiType.LONG)? 2 : 1;
                    }

                    @Override
                    public void visitClass(PsiClass aClass) {
                      // skip local and anonymous classes
                    }

                  });
                }
                finally {
                  myName = nameBuilder.toString();
                }
              }
            }
          }
        }
      }
    });
    return myValue;
  }

  public String getName() {
    return myName;
  }

  public String calcValueName() {
    return getName();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    if (!myParameterNameCalcutated) {
      return null;
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
    }
  }
}