// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface ExpectedParameter {

  @NotNull
  List<ExpectedType> getExpectedTypes();

  /**
   * For example, for unresolved call in Java {@code a.foo(bars)} this method will return 'bars' string,
   * which then will be used to suggest parameter names
   * taking code style parameter prefix into consideration as well as its type.
   */
  default @NotNull Collection<String> getSemanticNames() {
    return Collections.emptyList();
  }

  default @NotNull @Unmodifiable Collection<AnnotationRequest> getExpectedAnnotations() {
    return Collections.emptyList();
  }

}
