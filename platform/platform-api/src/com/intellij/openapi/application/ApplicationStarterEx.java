// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementers of the interface declared via {@link ApplicationStarter#EP_NAME}
 * may be capable of processing an external command line within a running IntelliJ Platform instance.
 *
 * @author yole
 */
public abstract class ApplicationStarterEx implements ApplicationStarter {
  public abstract boolean isHeadless();

  public boolean canProcessExternalCommandLine() {
    return false;
  }

  public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) { }
}