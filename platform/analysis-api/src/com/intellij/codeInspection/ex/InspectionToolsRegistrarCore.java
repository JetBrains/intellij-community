/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class InspectionToolsRegistrarCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolsRegistrarCore");
  static Object instantiateTool(@NotNull Class<?> toolClass) {
    try {
      Constructor<?> constructor = toolClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      constructor.setAccessible(true);
      return constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (SecurityException e) {
      LOG.error(e);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (IllegalArgumentException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }

    return null;
  }
}
