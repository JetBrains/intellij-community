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
 * Class ThisEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThisEvaluator implements Evaluator {
  private final int myIterations;

  public ThisEvaluator() {
    myIterations = 0;
  }

  public ThisEvaluator(int iterations) {
    myIterations = iterations;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value objRef = context.getThisObject();
    if(myIterations > 0) {
      ObjectReference thisRef = (ObjectReference)objRef;
      for (int idx = 0; idx < myIterations && thisRef != null; idx++) {
        thisRef  = getOuterObject(thisRef);
      }
      objRef = thisRef;
    }
    if(objRef == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.this.not.avalilable"));
    }
    return objRef;
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static ObjectReference getOuterObject(ObjectReference objRef) {
    if (objRef == null) {
      return null;
    }
    List<Field> list = objRef.referenceType().fields();
    for (final Field field : list) {
      final String name = field.name();
      if (name != null && name.startsWith("this$")) {
        final ObjectReference rv = (ObjectReference)objRef.getValue(field);
        if (rv != null) {
          return rv;
        }
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "this";
  }
}
