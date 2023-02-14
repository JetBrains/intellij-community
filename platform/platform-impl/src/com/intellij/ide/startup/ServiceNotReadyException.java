// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

/**
 * If thrown during startup process, indicates that a given service (e.g. index) isn't yet available,
 * and the query should be re-attempted later.
 */
public final class ServiceNotReadyException extends ProcessCanceledException {
  public ServiceNotReadyException() {

  }

  public ServiceNotReadyException(@NotNull String message) {
    super(message);
  }
}
