/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.rt.debugger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author egor
 */
public class DefaultMethodInvoker {

  // only methods without arguments for now
  public static Object invoke(Object obj, String name)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

    Method method = obj.getClass().getMethod(name, null);
    if (method != null) {
      method.setAccessible(true);
      Object res = method.invoke(obj, null);
      method.setAccessible(false);
      return res;
    }
    return null;
  }
}
