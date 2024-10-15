// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class TypeEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.getOnlyItem;

public class TypeEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(TypeEvaluator.class);

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
      if (classLoader != null || lastRes.virtualMachine().equals(context.getVirtualMachineProxy().getVirtualMachine())) {
        return lastRes;
      }
    }
    DebugProcessImpl debugProcess = context.getDebugProcess();
    String typeName = myTypeName.getName(debugProcess);
    ReferenceType type;
    try {
      type = debugProcess.findClass(context, typeName, classLoader);
    }
    catch (EvaluateException e) {
      ReferenceType singleLoadedClass =
        getOnlyItem(filter(context.getVirtualMachineProxy().classesByName(typeName), ReferenceType::isPrepared));
      if (singleLoadedClass == null) {
        throw e;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Unable to find or load class " + typeName + " in the requested classloader " + classLoader +
                  ", will use the single loaded class " + singleLoadedClass + " from " + singleLoadedClass.classLoader());
      }
      type = singleLoadedClass;
    }
    if (type == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("error.class.not.loaded", typeName));
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
