// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.util.io.StreamUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author egor
 */
public class ClassLoadingUtils {
  private static final int BATCH_SIZE = 4096;
  private ClassLoadingUtils() {}

  public static ClassLoaderReference getClassLoader(EvaluationContext context, DebugProcess process) throws EvaluateException {
    try {
      // TODO [egor]: cache?
      ClassType loaderClass = (ClassType)process.findClass(context, "java.net.URLClassLoader", context.getClassLoader());
      Method ctorMethod = loaderClass.concreteMethodByName(JVMNameUtil.CONSTRUCTOR_NAME, "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
      ClassLoaderReference reference = (ClassLoaderReference)process.newInstance(context, loaderClass, ctorMethod, Arrays
        .asList(createURLArray(context), context.getClassLoader()));
      DebuggerUtilsEx.keep(reference, context);
      return reference;
    }
    catch (Exception e) {
      throw new EvaluateException("Error creating evaluation class loader: " + e, e);
    }
  }

  public static void defineClass(String name,
                                 byte[] bytes,
                                 EvaluationContext context,
                                 DebugProcess process,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
      Method defineMethod =
        ((ClassType)classLoader.referenceType()).concreteMethodByName("defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
      StringReference nameObj = proxy.mirrorOf(name);
      DebuggerUtilsEx.keep(nameObj, context);
      process.invokeMethod(context, classLoader, defineMethod,
                           Arrays.asList(nameObj,
                                         mirrorOf(bytes, context, process),
                                         proxy.mirrorOf(0),
                                         proxy.mirrorOf(bytes.length)));
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
  public static ClassType getHelperClass(Class cls, EvaluationContext evaluationContext) throws EvaluateException {
    // TODO [egor]: cache and load in bootstrap class loader
    String name = cls.getName();
    DebugProcess process = evaluationContext.getDebugProcess();
    try {
      return (ClassType)process.findClass(evaluationContext, name, evaluationContext.getClassLoader());
    } catch (EvaluateException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InvocationException) {
        if ("java.lang.ClassNotFoundException".equals(((InvocationException)cause).exception().type().name())) {
          // need to define
          ClassLoaderReference classLoader = getClassLoader(evaluationContext, process);
          try (InputStream stream = cls.getResourceAsStream("/" + name.replaceAll("[.]", "/") + ".class")) {
            if (stream == null) return null;
            defineClass(name, StreamUtil.loadFromStream(stream), evaluationContext, process, classLoader);
            ((EvaluationContextImpl)evaluationContext).setClassLoader(classLoader);
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

  private static ArrayReference createURLArray(EvaluationContext context)
    throws EvaluateException {
    ArrayType arrayType = (ArrayType)context.getDebugProcess().findClass(context, "java.net.URL[]", context.getClassLoader());
    ArrayReference arrayRef = arrayType.newInstance(0);
    DebuggerUtilsEx.keep(arrayRef, context);
    return arrayRef;
  }

  private static ArrayReference mirrorOf(byte[] bytes, EvaluationContext context, DebugProcess process)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    ArrayType arrayClass = (ArrayType)process.findClass(context, "byte[]", context.getClassLoader());
    ArrayReference reference = process.newInstance(arrayClass, bytes.length);
    DebuggerUtilsEx.keep(reference, context);
    List<Value> mirrors = new ArrayList<>(bytes.length);
    for (byte b : bytes) {
      mirrors.add(((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).mirrorOf(b));
    }

    if (DebuggerUtils.isAndroidVM(arrayClass.virtualMachine())) {
      // Android VM has a limited buffer size to receive JDWP data (see https://issuetracker.google.com/issues/73584940)
      setChuckByChunk(reference, mirrors);
    }
    else {
      reference.setValues(mirrors);
    }

    return reference;
  }

  private static void setChuckByChunk(ArrayReference reference, List<? extends Value> values)
    throws ClassNotLoadedException, InvalidTypeException {
    int loaded = 0;
    while (loaded < values.size()) {
      int chunkSize = Math.min(BATCH_SIZE, values.size() - loaded);
      reference.setValues(loaded, values, loaded, chunkSize);
      loaded += chunkSize;
    }
  }
}
