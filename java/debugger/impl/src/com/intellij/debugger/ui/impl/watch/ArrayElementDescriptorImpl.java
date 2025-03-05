// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

public class ArrayElementDescriptorImpl extends ValueDescriptorImpl implements ArrayElementDescriptor {
  private final int myIndex;
  private final ArrayReference myArray;
  private Ref<Value> myPresetValue;

  public ArrayElementDescriptorImpl(Project project, ArrayReference array, int index) {
    super(project);
    myArray = array;
    myIndex = index;
    setLvalue(true);
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public ArrayReference getArray() {
    return myArray;
  }

  @Override
  public String getName() {
    return String.valueOf(myIndex);
  }

  public void setValue(Value value) {
    myPresetValue = Ref.create(value);
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    if (myPresetValue != null) {
      return myPresetValue.get();
    }
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

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
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
      protected void setValueImpl(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
        final ArrayElementDescriptorImpl elementDescriptor = ArrayElementDescriptorImpl.this;
        final ArrayReference array = elementDescriptor.getArray();
        if (array != null) {
          if (VirtualMachineProxyImpl.isCollected(array)) {
            // will only be the case if debugger does not use ObjectReference.disableCollection() because of Patches.IBM_JDK_DISABLE_COLLECTION_BUG
            Messages.showWarningDialog(getProject(), JavaDebuggerBundle.message("evaluation.error.array.collected") + "\n" + JavaDebuggerBundle
              .message("warning.recalculate"), JavaDebuggerBundle.message("title.set.value"));
            //node.getParent().calcValue();
            return;
          }
          final ArrayType arrType = (ArrayType)array.referenceType();
          final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
          set(expression, callback, debuggerContext, new SetValueRunnable() {
            @Override
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue)
              throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
              array.setValue(elementDescriptor.getIndex(), preprocessValue(evaluationContext, newValue, getLType()));
              update(debuggerContext);
            }

            @Override
            public ClassLoaderReference getClassLoader(EvaluationContextImpl evaluationContext) {
              return arrType.classLoader();
            }

            @Override
            public @NotNull Type getLType() throws ClassNotLoadedException {
              return arrType.componentType();
            }
          });
        }
      }
    };
  }
}
