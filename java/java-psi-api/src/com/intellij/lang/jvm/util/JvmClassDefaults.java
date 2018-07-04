// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.filter;

/**
 * This class holds default implementations of {@link JvmClass} methods.
 */
public class JvmClassDefaults {

  private JvmClassDefaults() {}

  @NotNull
  public static JvmMethod[] findMethodsByName(@NotNull JvmClass clazz, @NotNull String methodName) {
    List<JvmMethod> result = filter(clazz.getMethods(), it -> it.getName().equals(methodName));
    return result.toArray(JvmMethod.EMPTY_ARRAY);
  }
}
