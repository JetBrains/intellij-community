// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider;

import com.intellij.platform.eel.path.EelPath;
import com.intellij.platform.eel.path.EelPathException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Java facade preserving binary compatibility for EEL ↔ NIO path conversion.
 * Kotlin callers should use the extension functions from {@code EelPathConversions.kt} directly.
 */
@ApiStatus.Experimental
public final class EelNioBridgeServiceKt {
  private EelNioBridgeServiceKt() {}

  public static @NotNull Path asNioPath(@NotNull EelPath eelPath) {
    return EelPathConversionsKt.asNioPath(eelPath);
  }

  public static @NotNull EelPath asEelPath(@NotNull Path nioPath) throws EelPathException {
    return EelPathConversionsKt.asEelPath(nioPath);
  }
}
