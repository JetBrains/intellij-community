// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ArrayAccessEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

class ArrayAccessEvaluator implements ModifiableEvaluator {
  private final Evaluator myArrayReferenceEvaluator;
  private final Evaluator myIndexEvaluator;

  // TODO remove non-final fields, see IDEA-366793
  @Deprecated
  private ArrayReference myEvaluatedArrayReference;
  @Deprecated
  private int myEvaluatedIndex;

  ArrayAccessEvaluator(Evaluator arrayReferenceEvaluator, Evaluator indexEvaluator) {
    myArrayReferenceEvaluator = arrayReferenceEvaluator;
    myIndexEvaluator = indexEvaluator;
  }

  @Override
  public @NotNull ModifiableValue evaluateModifiable(EvaluationContextImpl context) throws EvaluateException {
    if (!(myArrayReferenceEvaluator.evaluate(context) instanceof ArrayReference evaluatedArrayReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.array.reference.expected"));
    }
    Value indexValue = (Value)myIndexEvaluator.evaluate(context);
    if (!DebuggerUtils.isInteger(indexValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.index.expression"));
    }
    int evaluatedIndex = ((PrimitiveValue)indexValue).intValue();

    try {
      Value value = evaluatedArrayReference.getValue(evaluatedIndex);
      myEvaluatedArrayReference = evaluatedArrayReference;
      myEvaluatedIndex = evaluatedIndex;
      return new ModifiableValue(value, new MyModifier(evaluatedArrayReference, evaluatedIndex));
    }
    catch (Exception e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public Modifier getModifier() {
    if (myEvaluatedArrayReference != null) {
      return new MyModifier(myEvaluatedArrayReference, myEvaluatedIndex);
    }
    return null;
  }

  private static class MyModifier implements Modifier {
    private final ArrayReference myEvaluatedArrayReference;
    private final int myEvaluatedIndex;

    private MyModifier(ArrayReference evaluatedArrayReference, int evaluatedIndex) {
      myEvaluatedArrayReference = evaluatedArrayReference;
      myEvaluatedIndex = evaluatedIndex;
    }

    @Override
    public boolean canInspect() {
      return true;
    }

    @Override
    public boolean canSetValue() {
      return true;
    }

    @Override
    public void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException {
      myEvaluatedArrayReference.setValue(myEvaluatedIndex, value);
    }

    @Override
    public Type getExpectedType() throws EvaluateException {
      try {
        ArrayType type = (ArrayType)myEvaluatedArrayReference.referenceType();
        return type.componentType();
      }
      catch (ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }

    @Override
    public NodeDescriptorImpl getInspectItem(Project project) {
      return new ArrayElementDescriptorImpl(project, myEvaluatedArrayReference, myEvaluatedIndex);
    }
  }
}
