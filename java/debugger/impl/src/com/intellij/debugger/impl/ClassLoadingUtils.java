/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.rt.debugger.ImageSerializer;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author egor
 */
public class ClassLoadingUtils {
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
  public static ClassType getHelperClass(String name, EvaluationContext evaluationContext, DebugProcess process) throws EvaluateException {
    // TODO [egor]: cache and load in boostrap class loader
    try {
      ClassLoaderReference classLoader = evaluationContext.getClassLoader();
      return (ClassType)process.findClass(evaluationContext, name, classLoader);
    } catch (EvaluateException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InvocationException) {
        if ("java.lang.ClassNotFoundException".equals(((InvocationException)cause).exception().type().name())) {
          // need to define
          ClassLoaderReference classLoader = getClassLoader(evaluationContext, process);
          InputStream stream = ImageSerializer.class.getResourceAsStream("/" + name.replaceAll("[.]", "/") + ".class");
          try {
            if (stream == null) return null;
            defineClass(name, StreamUtil.loadFromStream(stream), evaluationContext, process, classLoader);
            ((EvaluationContextImpl)evaluationContext).setClassLoader(classLoader);
            return (ClassType)process.findClass(evaluationContext, name, classLoader);
          }
          catch (IOException ioe) {
            throw new EvaluateException("Unable to read " + name + " class bytes", ioe);
          }
          finally {
            try {
              if (stream != null) {
                stream.close();
              }
            }
            catch (IOException ignored) {}
          }
        }
      }
      throw e;
    }
  }

  private static ArrayReference createURLArray(EvaluationContext context)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    DebugProcess process = context.getDebugProcess();
    ArrayType arrayType = (ArrayType)process.findClass(context, "java.net.URL[]", context.getClassLoader());
    ArrayReference arrayRef = arrayType.newInstance(1);
    DebuggerUtilsEx.keep(arrayRef, context);
    ClassType classType = (ClassType)process.findClass(context, "java.net.URL", context.getClassLoader());
    VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
    StringReference url = proxy.mirrorOf("file:a");
    DebuggerUtilsEx.keep(url, context);
    ObjectReference reference = process.newInstance(context,
                                                    classType,
                                                    classType.concreteMethodByName(JVMNameUtil.CONSTRUCTOR_NAME, "(Ljava/lang/String;)V"),
                                                    Collections.singletonList(url));
    DebuggerUtilsEx.keep(reference, context);
    arrayRef.setValues(Collections.singletonList(reference));
    return arrayRef;
  }

  private static ArrayReference mirrorOf(byte[] bytes, EvaluationContext context, DebugProcess process)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    ArrayType arrayClass = (ArrayType)process.findClass(context, "byte[]", context.getClassLoader());
    ArrayReference reference = process.newInstance(arrayClass, bytes.length);
    DebuggerUtilsEx.keep(reference, context);
    for (int i = 0; i < bytes.length; i++) {
      reference.setValue(i, ((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).mirrorOf(bytes[i]));
    }
    return reference;
  }
}
