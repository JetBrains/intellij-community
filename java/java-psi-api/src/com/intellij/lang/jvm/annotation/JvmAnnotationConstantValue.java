// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16.1-130">const_value_index</a> value.
 */
public interface JvmAnnotationConstantValue extends JvmAnnotationAttributeValue {

  /**
   * @return primitive or string value or {@code null} if constant value cannot be computed
   */
  @Nullable
  Object getConstantValue();
}
