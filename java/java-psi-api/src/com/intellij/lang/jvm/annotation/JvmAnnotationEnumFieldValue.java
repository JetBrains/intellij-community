// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.lang.jvm.JvmField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16.1-130">enum_const_value</a> struct.
 */
public interface JvmAnnotationEnumFieldValue extends JvmAnnotationAttributeValue {

  /**
   * @return referenced enum field or {@code null} if field cannot be resolved
   */
  @Nullable
  JvmEnumField getField();

  /**
   * This method could be implemented to return value even if the field cannot be resolved.
   *
   * @return name of the referenced enum field
   */
  @Nullable
  default String getFieldName() {
    JvmField field = getField();
    return field == null ? null : field.getName();
  }

  /**
   * This method could be implemented to return value even if the field cannot be resolved.
   *
   * @return containing class of the referenced enum field
   */
  @Nullable
  default JvmClass getContainingClass() {
    JvmEnumField field = getField();
    return field == null ? null : field.getContainingClass();
  }

  /**
   * This method could be implemented to return value even if the field or the containing class cannot be resolved.
   *
   * @return fully qualified name of the containing class of the referenced enum field
   */
  @NonNls
  @Nullable
  default String getContainingClassName() {
    JvmClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getQualifiedName();
  }
}
