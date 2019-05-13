// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.find;

public interface JvmAnnotation extends JvmElement {

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @Nullable
  @NonNls
  String getQualifiedName();

  /**
   * This method is preferable to {@link #findAttribute(String)}
   * because it allows to provide more efficient implementation.
   *
   * @return {@code true} if this annotation has an attribute with the specified name, otherwise {@code false}
   */
  default boolean hasAttribute(@NonNls @NotNull String attributeName) {
    return findAttribute(attributeName) != null;
  }

  /**
   * This method is preferable to manual search in results of {@link #getAttributes()}
   * because it allows to provide more efficient implementation.
   *
   * @return attribute if this annotation has an attribute with specified name, otherwise {@code null}
   */
  @Nullable
  default JvmAnnotationAttribute findAttribute(@NonNls @NotNull String attributeName) {
    return find(getAttributes(), attribute -> attributeName.equals(attribute.getAttributeName()));
  }

  @NotNull
  List<JvmAnnotationAttribute> getAttributes();
}
