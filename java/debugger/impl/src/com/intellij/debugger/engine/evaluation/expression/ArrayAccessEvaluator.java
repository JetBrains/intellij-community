/*
 * Class ArrayAccessEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.sun.jdi.*;

class ArrayAccessEvaluator implements Evaluator {
  private final Evaluator myArrayReferenceEvaluator;
  private final Evaluator myIndexEvaluator;
  private ArrayReference myEvaluatedArrayReference;
  private int myEvaluatedIndex;

  public ArrayAccessEvaluator(Evaluator arrayReferenceEvaluator, Evaluator indexEvaluator) {
    myArrayReferenceEvaluator = arrayReferenceEvaluator;
    myIndexEvaluator = indexEvaluator;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    myEvaluatedIndex = 0;
    myEvaluatedArrayReference = null;
    Value indexValue = (Value)myIndexEvaluator.evaluate(context);
    Value arrayValue = (Value)myArrayReferenceEvaluator.evaluate(context);
    if (!(arrayValue instanceof ArrayReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.array.reference.expected"));
    }
    myEvaluatedArrayReference = (ArrayReference)arrayValue;
    if (!DebuggerUtilsEx.isInteger(indexValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.index.expression"));
    }
    myEvaluatedIndex = ((PrimitiveValue)indexValue).intValue();
    try {
      return myEvaluatedArrayReference.getValue(myEvaluatedIndex);
    }
    catch (Exception e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public Modifier getModifier() {
    Modifier modifier = null;
    if (myEvaluatedArrayReference != null) {
      modifier = new Modifier() {
        public boolean canInspect() {
          return true;
        }

        public boolean canSetValue() {
          return true;
        }

        public void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException {
          myEvaluatedArrayReference.setValue(myEvaluatedIndex, value);
        }

        public Type getExpectedType() throws EvaluateException {
          try {
            ArrayType type = (ArrayType)myEvaluatedArrayReference.referenceType();
            return type.componentType();
          }
          catch (ClassNotLoadedException e) {
            throw EvaluateExceptionUtil.createEvaluateException(e);
          }
        }

        public NodeDescriptorImpl getInspectItem(Project project) {
          return new ArrayElementDescriptorImpl(project, myEvaluatedArrayReference, myEvaluatedIndex);
        }
      };
    }
    return modifier;
  }
}
