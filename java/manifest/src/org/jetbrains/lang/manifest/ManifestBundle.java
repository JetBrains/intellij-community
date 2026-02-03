// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.lang.manifest;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ManifestBundle {
  private static final String BUNDLE = "messages.ManifestBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ManifestBundle.class, BUNDLE);

  private ManifestBundle() {
  }

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
