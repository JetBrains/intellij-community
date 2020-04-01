// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@ApiStatus.Internal
public class PreviewUtil {
  /**
   * @param object object to check
   * @return false if it's determined that given object doesn't refer directly or indirectly 
   * to any specific file (PSI, document, editor, etc.).  
   */
  public static boolean mayBeFileBound(Object object) {
    for (Field field : ReflectionUtil.collectFields(object.getClass())) {
      if (Modifier.isStatic(field.getModifiers())) continue;
      Class<?> type = field.getType();
      if (type.isPrimitive() || type.isEnum() || type.equals(String.class) ||
          type.equals(Integer.class) || type.equals(Boolean.class)) continue;
      return true;
    }
    return false;
  }
}
