// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

class JdiHelperClassLoaderImpl implements JdiHelperClassLoader {
  @Override
  public @Nullable ClassType getHelperClass(Class<?> cls, @NotNull EvaluationContextImpl evaluationContext,
                                            String @NotNull ... additionalClassesToLoad) throws EvaluateException {
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
          ClassLoaderReference classLoaderForDefine = newClassLoader ? ClassLoadingUtils.getClassLoader(evaluationContext, process)
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
      Value parent = process.invokeInstanceMethod(
        evaluationContext, classLoader, parentMethod,
        Collections.emptyList(), 0, true
      );
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
      ClassLoadingUtils.defineClass(
        name, stream.readAllBytes(), evaluationContext, evaluationContext.getDebugProcess(), classLoader
      );
      return true;
    }
    catch (IOException ioe) {
      throw new EvaluateException("Unable to read " + name + " class bytes", ioe);
    }
  }
}
