// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NotNull;

public abstract class ActiveRunnable extends ComparableObject.Impl {
  protected ActiveRunnable() {
  }

  protected ActiveRunnable(@NotNull Object object) {
    super(object);
  }

  protected ActiveRunnable(Object @NotNull [] objects) {
    super(objects);
  }

  public abstract @NotNull ActionCallback run();
}