// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles interactive input requests from ssh, such as a passphrase request, an unknown server key confirmation, etc.
 */
public interface GitNativeSshAuthenticator {
  @Nullable
  String handleInput(@NotNull String description);
}
