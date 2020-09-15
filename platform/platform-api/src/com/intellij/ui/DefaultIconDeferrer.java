// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class DefaultIconDeferrer extends IconDeferrer {
  @Override
  public <T> @NotNull Icon defer(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return f.fun(param);
  }

  @Override
  public <T> Icon deferAutoUpdatable(Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return f.fun(param);
  }
}