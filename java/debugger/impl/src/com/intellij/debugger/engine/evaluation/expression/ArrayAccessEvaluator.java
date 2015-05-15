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

/*
 * Class ArrayAccessEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
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
    if (!DebuggerUtils.isInteger(indexValue)) {
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
