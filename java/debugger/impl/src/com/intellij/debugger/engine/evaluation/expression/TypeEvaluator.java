/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
 * Class TypeEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.reference.SoftReference;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

public class TypeEvaluator implements Evaluator {
  private final JVMName myTypeName;

  private WeakReference<ReferenceType> myLastResult;
  private WeakReference<ClassLoaderReference> myLastClassLoader;

  public TypeEvaluator(@NotNull JVMName typeName) {
    myTypeName = typeName;
  }

  /**
   * @return ReferenceType in the target VM, with the given fully qualified name
   */
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    ClassLoaderReference classLoader = context.getClassLoader();
    ReferenceType lastRes = SoftReference.dereference(myLastResult);
    if (lastRes != null && classLoader == SoftReference.dereference(myLastClassLoader)) {
      // if class loader is null, check that vms match
      if (classLoader != null || lastRes.virtualMachine().equals(context.getDebugProcess().getVirtualMachineProxy().getVirtualMachine())) {
        return lastRes;
      }
    }
    DebugProcessImpl debugProcess = context.getDebugProcess();
    String typeName = myTypeName.getName(debugProcess);
    ReferenceType type = debugProcess.findClass(context, typeName, classLoader);
    if (type == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", typeName));
    }
    myLastClassLoader = new WeakReference<>(classLoader);
    myLastResult = new WeakReference<>(type);
    return type;
  }

  @Override
  public String toString() {
    return "Type " + myTypeName;
  }
}
