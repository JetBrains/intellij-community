// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.getOnlyItem;

public class TypeEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(TypeEvaluator.class);

  private final JVMName myTypeName;

  public TypeEvaluator(@NotNull JVMName typeName) {
    myTypeName = typeName;
  }

  /**
   * @return ReferenceType in the target VM, with the given fully qualified name
   */
  @Override
  public @NotNull ReferenceType evaluate(EvaluationContextImpl context) throws EvaluateException {
    ClassLoaderReference classLoader = context.getClassLoader();
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
    return type;
  }

  @Override
  public String toString() {
    return "Type " + myTypeName;
  }
}
