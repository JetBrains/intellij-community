// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface ExpectedParameter {

  @NotNull
  List<ExpectedType> getExpectedTypes();

  /**
   * For example for unresolved call in Java {@code a.foo(bars)} this method will return 'bars' string,
   * which then will be used to suggest parameter names
   * taking code style parameter prefix into consideration as well as its type.
   */
  @NotNull
  default Collection<String> getSemanticNames() {
    return Collections.emptyList();
  }
}
