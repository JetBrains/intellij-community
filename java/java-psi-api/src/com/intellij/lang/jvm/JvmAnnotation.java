// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface JvmAnnotation extends JvmElement {

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @Nullable
  @NonNls
  String getQualifiedName();

  @NotNull
  List<JvmAnnotationAttribute> getAttributes();
}
