// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmTypeDeclaration;
import com.intellij.lang.jvm.types.JvmReferenceType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;

public class JvmUtil {

  private JvmUtil() {}

  @NotNull
  static Iterable<JvmClass> resolveClasses(@NotNull JvmReferenceType[] types) {
    return mapNotNull(types, JvmUtil::resolveClass);
  }

  @Contract("null -> null")
  @Nullable
  public static JvmClass resolveClass(@Nullable JvmReferenceType type) {
    if (type == null) return null;
    JvmTypeDeclaration resolved = type.resolve();
    return resolved instanceof JvmClass ? (JvmClass)resolved : null;
  }
}
