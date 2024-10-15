// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Arrays;

public final class MethodInvoker {
  public static Object invoke(MethodHandles.Lookup lookup,
                              Class<?> cls,
                              Object obj,
                              String nameAndDescriptor,
                              Object[] argsArray,
                              ClassLoader loader)
    throws Throwable {
    try {
      int separatorIndex = nameAndDescriptor.indexOf(';');
      String name = nameAndDescriptor.substring(0, separatorIndex);
      String descriptor = nameAndDescriptor.substring(separatorIndex + 1);

      MethodType mt = MethodType.fromMethodDescriptorString(descriptor, loader);
      MethodHandle method;
      if ("<init>".equals(name)) {
        method = lookup.findConstructor(cls, mt);
      }
      else if (obj != null) {
        method = lookup.findVirtual(cls, name, mt).bindTo(obj);
      }
      else {
        method = lookup.findStatic(cls, name, mt);
      }

      // the last element is always null, it is reserved for the return value
      Object[] args = Arrays.copyOf(argsArray, argsArray.length - 1);

      Object result;

      // handle the case where null is passed as the vararg array
      // TODO: handle when vararg is not the only parameter
      if (mt.parameterCount() == 1 && args.length == 1 && args[0] == null && mt.parameterType(0).isArray()) {
        result = method.invoke((Object[])null);
      }
      else {
        result = method.invokeWithArguments(args);
      }
      argsArray[argsArray.length - 1] = result; // store the result as the last array element to avoid it being collected
      return result;
    }
    catch (WrongMethodTypeException | ClassCastException e) {
      e.printStackTrace();
      throw e;
    }
  }
}
