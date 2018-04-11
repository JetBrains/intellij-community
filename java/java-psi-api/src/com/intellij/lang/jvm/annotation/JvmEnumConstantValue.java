// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.lang.jvm.JvmField;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16.1-130">enum_const_value</a> struct.
 */
public interface JvmEnumConstantValue extends JvmAttributeValue {

  /**
   * This method could be implemented to return class value even if field is unresolved
   *
   * @return
   */
  @Nullable
  default JvmClassValue getClassValue() {
    JvmField field = getField();
    JvmClass clazz = field == null ? null : field.getContainingClass();
    return clazz == null ? null : new JvmEnumConstantClassValue(this, clazz);
  }

  /**
   * This method could be implemented to return enum field name if field is unresolved
   *
   * @return enum field referenced name
   */
  @Nullable
  default String getName() {
    JvmField field = getField();
    return field == null ? null : field.getName();
  }

  /**
   * @return referenced enum field
   */
  @Nullable
  JvmEnumField getField();
}
