// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import com.intellij.lang.jvm.JvmAnnotation;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16.1-130">annotation_value</a> struct.
 */
public interface JvmNestedAnnotationValue extends JvmAnnotationAttributeValue {

  @NotNull
  JvmAnnotation getValue();
}
