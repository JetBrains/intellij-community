// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.io.StreamUtil;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ClassLoadingUtils {
  private static final Logger LOG = Logger.getInstance(ClassLoadingUtils.class);
  private static final int BATCH_SIZE = 4096;
  private ClassLoadingUtils() {}

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
                                 EvaluationContext context,
                                 DebugProcess process,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
      Method defineMethod =
        DebuggerUtils.findMethod(classLoader.referenceType(), "defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
      ((DebugProcessImpl)process).invokeInstanceMethod(context, classLoader, defineMethod,
                                                       Arrays.asList(DebuggerUtilsEx.mirrorOfString(name, proxy, context),
                                                                     mirrorOf(bytes, context, process),
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
  public static ClassType getHelperClass(Class<?> cls, EvaluationContext evaluationContext) throws EvaluateException {
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

  private static ArrayReference mirrorOf(byte[] bytes, EvaluationContext context, DebugProcess process)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    ArrayType arrayClass = (ArrayType)process.findClass(context, "byte[]", context.getClassLoader());
    ArrayReference reference = DebuggerUtilsEx.mirrorOfArray(arrayClass, bytes.length, context);
    VirtualMachine virtualMachine = reference.virtualMachine();
    List<Value> mirrors = new ArrayList<>(bytes.length);
    for (byte b : bytes) {
      mirrors.add(virtualMachine.mirrorOf(b));
    }

    if (DebuggerUtils.isAndroidVM(virtualMachine)) {
      // Android VM has a limited buffer size to receive JDWP data (see https://issuetracker.google.com/issues/73584940)
      setChuckByChunk(reference, mirrors);
    }
    else {
      try {
        DebuggerUtilsEx.setValuesNoCheck(reference, mirrors);
      }
      catch (VMMismatchException e) {
        LOG.error("Class vm: " + arrayClass.virtualMachine() +
                  " loaded by " + arrayClass.virtualMachine().getClass().getClassLoader() +
                  "\nReference vm: " + reference.virtualMachine() +
                  " loaded by " + reference.virtualMachine().getClass().getClassLoader() +
                  "\nMirrors vms: " + StreamEx.of(mirrors).map(Mirror::virtualMachine).distinct()
                    .map(vm -> {
                      return vm +
                             " loaded by " + vm.getClass().getClassLoader() +
                             " same as ref vm = " + (vm == reference.virtualMachine());
                    })
                    .joining(", ")
          , e);
      }
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
