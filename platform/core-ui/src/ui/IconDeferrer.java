// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Function;

public abstract class IconDeferrer {
  public static IconDeferrer getInstance() {
    return ApplicationManager.getApplication().getService(IconDeferrer.class);
  }

  public abstract <T> @NotNull Icon defer(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f);

  public abstract <T> Icon deferAutoUpdatable(Icon base, T param, @NotNull Function<? super T, ? extends Icon> f);

  public abstract void clearCache();

  public boolean equalIcons(Icon icon1, Icon icon2) {
    return Objects.equals(icon1, icon2);
  }
}