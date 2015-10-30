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
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.JavaValueModifier;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalVariableDescriptorImpl extends ValueDescriptorImpl implements LocalVariableDescriptor {
  private final StackFrameProxyImpl myFrameProxy;
  private final LocalVariableProxyImpl myLocalVariable;

  private String myTypeName = DebuggerBundle.message("label.unknown.value");
  private boolean myIsPrimitive;

  private boolean myIsNewLocal = true;

  public LocalVariableDescriptorImpl(Project project,
                                     @NotNull LocalVariableProxyImpl local) {
    super(project);
    setLvalue(true);
    myFrameProxy = local.getFrame();
    myLocalVariable = local;
  }

  @Override
  public LocalVariableProxyImpl getLocalVariable() {
    return myLocalVariable;
  }

  public boolean isNewLocal() {
    return myIsNewLocal;
  }

  @Override
  public boolean isPrimitive() {
    return myIsPrimitive;
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    boolean isVisible = myFrameProxy.isLocalVariableVisible(getLocalVariable());
    if (isVisible) {
      final String typeName = getLocalVariable().typeName();
      myTypeName = typeName;
      myIsPrimitive = DebuggerUtils.isPrimitiveType(typeName);
      return myFrameProxy.getValue(getLocalVariable());
    }

    return null;
  }

  public void setNewLocal(boolean aNew) {
    myIsNewLocal = aNew;
  }

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    super.displayAs(descriptor);
    if(descriptor instanceof LocalVariableDescriptorImpl) {
      myIsNewLocal = ((LocalVariableDescriptorImpl)descriptor).myIsNewLocal;
    }
  }

  @Override
  public String getName() {
    return myLocalVariable.name();
  }

  @Nullable
  @Override
  public String getDeclaredType() {
    return myTypeName;
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
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
      protected void setValueImpl(@NotNull String expression, @NotNull XModificationCallback callback) {
        final LocalVariableProxyImpl local = LocalVariableDescriptorImpl.this.getLocalVariable();
        if (local != null) {
          final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
          set(expression, callback, debuggerContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                                 InvalidTypeException,
                                                                                                 EvaluateException {
              debuggerContext.getFrameProxy().setValue(local, preprocessValue(evaluationContext, newValue, local.getType()));
              update(debuggerContext);
            }

            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                             ClassNotLoadedException,
                                                                                                             IncompatibleThreadStateException,
                                                                                                             InvalidTypeException,
                                                                                                             EvaluateException {
              return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                   evaluationContext.getClassLoader());
            }
          });
        }
      }
    };
  }
}