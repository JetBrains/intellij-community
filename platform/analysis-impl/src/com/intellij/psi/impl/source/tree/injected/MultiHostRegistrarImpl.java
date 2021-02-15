// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link MultiHostRegistrar} instead. to be removed in IDEA 2018.1
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2018.1")
@Deprecated
public abstract class MultiHostRegistrarImpl implements MultiHostRegistrar {

  /**
   * @deprecated Use {@link MultiHostRegistrar#startInjecting(Language)} instead. to be removed in IDEA 2018.1
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2018.1")
  @Deprecated
  @NotNull
  @Override
  public MultiHostRegistrar startInjecting(@NotNull Language language) {
    throw new IllegalStateException();
  }
}
