// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.annotation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single element from <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16">element_value_pairs</a>
 * in an annotation.
 */
public interface JvmAnnotationAttribute {

  @NotNull
  String getAttributeName();

  @Nullable
  JvmAnnotationAttributeValue getAttributeValue();
}
