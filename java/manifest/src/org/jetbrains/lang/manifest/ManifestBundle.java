// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.lang.manifest;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class ManifestBundle extends DynamicBundle {

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static final String PATH_TO_BUNDLE = "messages.ManifestBundle";
  private static final ManifestBundle BUNDLE = new ManifestBundle();

  private ManifestBundle() {
    super(PATH_TO_BUNDLE);
  }
}
