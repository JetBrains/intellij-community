// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public final class DefaultIconDeferrer extends IconDeferrer {
  @Override
  public <T> @NotNull Icon defer(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return f.apply(param);
  }

  @Override
  public <T> Icon deferAutoUpdatable(Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return f.apply(param);
  }

  @Override
  public void clearCache() {
  }
}