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
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.JavaValueModifier;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.tree.ArrayElementDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

public class ArrayElementDescriptorImpl extends ValueDescriptorImpl implements ArrayElementDescriptor{
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

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getArrayElement(myArray, myIndex);
  }

  public static Value getArrayElement(ArrayReference reference, int idx) throws EvaluateException {
    try {
      return reference.getValue(idx);
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

  @Override
  public XValueModifier getModifier(JavaValue value) {
    return new JavaValueModifier(value) {
      @Override
      protected void setValueImpl(@NotNull String expression, @NotNull XModificationCallback callback) {
        final ArrayElementDescriptorImpl elementDescriptor = ArrayElementDescriptorImpl.this;
        final ArrayReference array = elementDescriptor.getArray();
        if (array != null) {
          if (VirtualMachineProxyImpl.isCollected(array)) {
            // will only be the case if debugger does not use ObjectReference.disableCollection() because of Patches.IBM_JDK_DISABLE_COLLECTION_BUG
            Messages.showWarningDialog(getProject(), DebuggerBundle.message("evaluation.error.array.collected") + "\n" + DebuggerBundle.message("warning.recalculate"), DebuggerBundle.message("title.set.value"));
            //node.getParent().calcValue();
            return;
          }
          final ArrayType arrType = (ArrayType)array.referenceType();
          final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
          set(expression, callback, debuggerContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue)
              throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
              array.setValue(elementDescriptor.getIndex(), preprocessValue(evaluationContext, newValue, arrType.componentType()));
              update(debuggerContext);
            }

            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                             ClassNotLoadedException,
                                                                                                             IncompatibleThreadStateException,
                                                                                                             InvalidTypeException,
                                                                                                             EvaluateException {
              return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, arrType.classLoader());
            }
          });
        }
      }
    };
  }
}
