// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class BuiltInServerBundle {
  private static final @NonNls String BUNDLE = "messages.BuiltInServerBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BuiltInServerBundle.class, BUNDLE);

  private BuiltInServerBundle() {
  }

  public static @NotNull @Nls String getReloadOnSaveGotItTitle() {
    return message("reload.on.save.got.it.title");
  }

  public static @NotNull @Nls String getReloadOnSaveGotItContent() {
    return message("reload.on.save.got.it.content");
  }

  public static @NotNull @Nls String getReloadOnSavePreviewGotItTitle() {
    return message("reload.on.save.preview.got.it.title");
  }

  public static @NotNull @Nls String getReloadOnSavePreviewGotItContent() {
    return message("reload.on.save.preview.got.it.content");
  }

  @ApiStatus.Internal
  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @ApiStatus.Internal
  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
