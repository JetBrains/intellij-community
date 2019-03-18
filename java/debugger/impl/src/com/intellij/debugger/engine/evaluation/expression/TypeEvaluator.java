// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  @NotNull
  @Override
  public ReferenceType evaluate(EvaluationContextImpl context) throws EvaluateException {
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
