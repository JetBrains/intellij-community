// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.JavaValueModifier;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArgumentValueDescriptorImpl extends ValueDescriptorImpl{
  private final DecompiledLocalVariable myVariable;

  public ArgumentValueDescriptorImpl(Project project, DecompiledLocalVariable variable, Value value) {
    super(project, value);
    myVariable = variable;
    setLvalue(true);
  }

  @Override
  public boolean canSetValue() {
    return LocalVariablesUtil.canSetValues();
  }

  public boolean isPrimitive() {
    return getValue() instanceof PrimitiveValue;
  }

  public Value calcValue(final EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getValue();
  }

  public DecompiledLocalVariable getVariable() {
    return myVariable;
  }

  public String getName() {
    return myVariable.getDisplayName();
  }

  public boolean isParameter() {
    return myVariable.isParam();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
    }
  }

  @Override
  public XValueModifier getModifier(JavaValue value) {
    return new JavaValueModifier(value) {
      @Override
      protected void setValueImpl(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
        final DecompiledLocalVariable local = ArgumentValueDescriptorImpl.this.getVariable();
        if (local != null) {
          final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
          set(expression, callback, debuggerContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                                 InvalidTypeException,
                                                                                                 EvaluateException {
              LocalVariablesUtil.setValue(debuggerContext.getFrameProxy().getStackFrame(), local.getSlot(), newValue);
              update(debuggerContext);
            }

            @Nullable
            @Override
            public Type getLType() {
              return null;
            }
          });
        }
      }
    };
  }
}