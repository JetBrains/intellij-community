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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
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
      StringReference nameString = DebuggerUtilsEx.mirrorOfString(name, context);
      ArrayReference byteArray = DebuggerUtilsEx.mirrorOfByteArray(bytes, context);
      try {
        ((DebugProcessImpl)process).invokeInstanceMethod(context, classLoader, defineMethod,
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
  @Nullable
  public static ClassType getHelperClass(Class<?> cls, EvaluationContextImpl evaluationContext,
                                         String... additionalClassesToLoad) throws EvaluateException {
    String name = cls.getName();
    evaluationContext = evaluationContext.withAutoLoadClasses(true);
    DebugProcess process = evaluationContext.getDebugProcess();
    ClassLoaderReference currentClassLoader = evaluationContext.getClassLoader();
    try {
      return (ClassType)process.findClass(evaluationContext, name, currentClassLoader);
    }
    catch (EvaluateException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InvocationException) {
        if ("java.lang.ClassNotFoundException".equals(((InvocationException)cause).exception().type().name())) {
          // need to define
          boolean newClassLoader = Registry.is("debugger.evaluate.load.helper.in.separate.classloader") || currentClassLoader == null;
          ClassLoaderReference classLoaderForDefine = newClassLoader ? getClassLoader(evaluationContext, process)
                                                                     : getTopClassloader(evaluationContext, currentClassLoader);

          for (String fqn : ContainerUtil.prepend(Arrays.asList(additionalClassesToLoad), name)) {
            if (!defineClass(fqn, cls, evaluationContext, classLoaderForDefine)) return null;
          }

          ClassLoaderReference classLoaderForFind = newClassLoader ? classLoaderForDefine : currentClassLoader;
          if (newClassLoader) {
            evaluationContext.setClassLoader(classLoaderForDefine);
          }
          return (ClassType)process.findClass(evaluationContext, name, classLoaderForFind);
        }
      }
      throw e;
    }
  }

  /**
   * Determines the top-level class loader in a hierarchy, starting from the given {@code currentClassLoader}.
   * <p>
   * It is used to define the helper class to avoid defining it in every classloader for performance reasons
   */
  private static @NotNull ClassLoaderReference getTopClassloader(EvaluationContextImpl evaluationContext,
                                                                 ClassLoaderReference currentClassLoader) throws EvaluateException {
    DebugProcessImpl process = evaluationContext.getDebugProcess();
    ReferenceType classLoaderClass = process.findClass(evaluationContext, "java.lang.ClassLoader", currentClassLoader);
    Method parentMethod = DebuggerUtils.findMethod(classLoaderClass, "getParent", "()Ljava/lang/ClassLoader;");
    Objects.requireNonNull(parentMethod, "getParent method is not available");

    ClassLoaderReference classLoader = currentClassLoader;

    while (true) {
      Value parent = process.invokeInstanceMethod(evaluationContext, classLoader, parentMethod, Collections.emptyList(), 0, true);
      if (!(parent instanceof ClassLoaderReference classLoaderReference)) {
        return classLoader;
      }
      classLoader = classLoaderReference;
    }
  }

  private static boolean defineClass(String name,
                                     Class<?> cls,
                                     EvaluationContextImpl evaluationContext,
                                     ClassLoaderReference classLoader) throws EvaluateException {
    try (InputStream stream = cls.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
      if (stream == null) return false;
      defineClass(name, stream.readAllBytes(), evaluationContext, evaluationContext.getDebugProcess(), classLoader);
      return true;
    }
    catch (IOException ioe) {
      throw new EvaluateException("Unable to read " + name + " class bytes", ioe);
    }
  }
}
