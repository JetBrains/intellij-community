// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.AbstractBundle;
import com.intellij.BundleBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public final class BootstrapBundle {
  private static final String BUNDLE = "messages.BootstrapBundle";

  private static final @Nullable AbstractBundle INSTANCE;

  static {
    AbstractBundle instance = null;
    try {
      instance = new AbstractBundle(BUNDLE);
    }
    catch (Throwable ignored) { }
    INSTANCE = instance;
  }

  private BootstrapBundle() {
  }

  // used for reporting startup errors, hence must not produce any exceptions
  public static @Nls @NotNull String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE != null) {
      try {
        return BundleBase.messageOrDefault(INSTANCE.getResourceBundle(BootstrapBundle.class.getClassLoader()), key, null, params);
      }
      catch (Throwable ignored) { }
    }

    StringBuilder sb = new StringBuilder();
    sb.append('!').append(key).append('!');
    for (Object param : params) {
      sb.append(param).append('!');
    }
    return sb.toString();  // NON-NLS (fallback)
  }
}
