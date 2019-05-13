// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import com.intellij.lang.jvm.JvmClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16.1-130">class_info_index</a> value.
 */
public interface JvmAnnotationClassValue extends JvmAnnotationAttributeValue {

  /**
   * This method could be implemented to return value even if class is unresolved
   *
   * @return referenced class fully qualified name
   */
  @NonNls
  @Nullable
  default String getQualifiedName() {
    JvmClass clazz = getClazz();
    return clazz == null ? null : clazz.getQualifiedName();
  }

  /**
   * @return referenced class or {@code null} if class cannot be resolved
   */
  @Nullable
  JvmClass getClazz();
}
