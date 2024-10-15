// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

public final class ClassLoadingUtils {
  private ClassLoadingUtils() { }

  public static ClassLoaderReference getClassLoader(EvaluationContext context, DebugProcess process) throws EvaluateException {
    try {
      ClassType loaderClass = (ClassType)process.findClass(context, "java.security.SecureClassLoader", context.getClassLoader());
      Method ctorMethod = DebuggerUtils.findMethod(loaderClass, JVMNameUtil.CONSTRUCTOR_NAME, "(Ljava/lang/ClassLoader;)V");
      return context.computeAndKeep(() -> (ClassLoaderReference)((DebugProcessImpl)process)
        .newInstance(context, loaderClass, ctorMethod, Collections.singletonList(context.getClassLoader()),
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
      ((DebugProcessImpl)process).invokeInstanceMethod(context, classLoader, defineMethod,
                                                       Arrays.asList(DebuggerUtilsEx.mirrorOfString(name, context),
                                                                     DebuggerUtilsEx.mirrorOfByteArray(bytes, context),
                                                                     proxy.mirrorOf(0),
                                                                     proxy.mirrorOf(bytes.length)),
                                                       MethodImpl.SKIP_ASSIGNABLE_CHECK,
                                                       true);
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
  @Nullable
  public static ClassType getHelperClass(Class<?> cls, EvaluationContextImpl evaluationContext) throws EvaluateException {
    // TODO [egor]: cache and load in bootstrap class loader
    String name = cls.getName();
    evaluationContext = evaluationContext.withAutoLoadClasses(true);
    DebugProcess process = evaluationContext.getDebugProcess();
    try {
      return (ClassType)process.findClass(evaluationContext, name, evaluationContext.getClassLoader());
    }
    catch (EvaluateException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InvocationException) {
        if ("java.lang.ClassNotFoundException".equals(((InvocationException)cause).exception().type().name())) {
          // need to define
          ClassLoaderReference classLoader = getClassLoader(evaluationContext, process);
          try (InputStream stream = cls.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
            if (stream == null) return null;
            defineClass(name, stream.readAllBytes(), evaluationContext, process, classLoader);
            evaluationContext.setClassLoader(classLoader);
            return (ClassType)process.findClass(evaluationContext, name, classLoader);
          }
          catch (IOException ioe) {
            throw new EvaluateException("Unable to read " + name + " class bytes", ioe);
          }
        }
      }
      throw e;
    }
  }
}
