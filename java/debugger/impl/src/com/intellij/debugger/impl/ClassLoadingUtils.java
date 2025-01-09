// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.debugger.impl.DebuggerUtilsEx.enableCollection;

public final class ClassLoadingUtils {
  private ClassLoadingUtils() { }

  public static ClassLoaderReference getClassLoader(EvaluationContext context, DebugProcess process) throws EvaluateException {
    try {
      ClassType loaderClass = (ClassType)process.findClass(context, "java.security.SecureClassLoader", context.getClassLoader());
      Method ctorMethod = DebuggerUtils.findMethod(loaderClass, JVMNameUtil.CONSTRUCTOR_NAME, "(Ljava/lang/ClassLoader;)V");
      return context.computeAndKeep(() -> (ClassLoaderReference)((DebugProcessImpl)process)
        .newInstance(context, loaderClass, Objects.requireNonNull(ctorMethod), Collections.singletonList(context.getClassLoader()),
                     MethodImpl.SKIP_ASSIGNABLE_CHECK, true));
    }
    catch (VMDisconnectedException e) {
      throw e;
    }
    catch (Exception e) {
      throw new EvaluateException("Error creating evaluation class loader: " + e, e);
    }
  }

  public static void defineClass(String name,
                                 byte[] bytes,
                                 EvaluationContextImpl context,
                                 DebugProcess process,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      VirtualMachineProxyImpl proxy = context.getVirtualMachineProxy();
      Method defineMethod =
        DebuggerUtils.findMethod(classLoader.referenceType(), "defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
      StringReference nameString = DebuggerUtilsEx.mirrorOfString(name, context);
      ArrayReference byteArray = DebuggerUtilsEx.mirrorOfByteArray(bytes, context);
      try {
        ((DebugProcessImpl)process).invokeInstanceMethod(context, classLoader, Objects.requireNonNull(defineMethod),
                                                         Arrays.asList(nameString,
                                                                       byteArray,
                                                                       proxy.mirrorOf(0),
                                                                       proxy.mirrorOf(bytes.length)),
                                                         MethodImpl.SKIP_ASSIGNABLE_CHECK,
                                                         true);
      }
      finally {
        enableCollection(nameString);
        enableCollection(byteArray);
      }
    }
    catch (VMDisconnectedException e) {
      throw e;
    }
    catch (Exception e) {
      throw new EvaluateException("Error during class " + name + " definition: " + e, e);
    }
  }

  /**
   * Finds and if necessary defines helper class
   * May modify class loader in evaluationContext
   */
  public static @Nullable ClassType getHelperClass(Class<?> cls, EvaluationContextImpl evaluationContext,
                                                   String... additionalClassesToLoad) {
    for (JdiHelperClassLoader loader : JdiHelperClassLoader.getLoaders()) {
      try {
        ClassType classType = loader.getHelperClass(cls, evaluationContext, additionalClassesToLoad);
        if (classType != null) {
          return classType;
        }
      }
      catch (EvaluateException ex) {
        String message = String.format("Failed to load '%s' with %s", cls.getName(), loader.getClass().getName());
        Logger.getInstance(ClassLoadingUtils.class).error(message, ex);
      }
    }
    return null;
  }
}
