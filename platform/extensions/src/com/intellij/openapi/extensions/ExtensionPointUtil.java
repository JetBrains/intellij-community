// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ClearableLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtensionPointUtil {

  @NotNull
  public static <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                          @NotNull ExtensionPointName<?> extensionPointName,
                                                                          @Nullable Disposable parentDisposable) {
    return dropLazyValueOnChange(lazyValue, extensionPointName.getPoint(null), parentDisposable);
  }

  @NotNull
  public static <V extends ClearableLazyValue<?>> V dropLazyValueOnChange(@NotNull V lazyValue,
                                                                          @NotNull ExtensionPoint<?> extensionPoint,
                                                                          @Nullable Disposable parentDisposable) {
    extensionPoint.addExtensionPointListener(lazyValue::drop, false, parentDisposable);
    return lazyValue;
  }
}
