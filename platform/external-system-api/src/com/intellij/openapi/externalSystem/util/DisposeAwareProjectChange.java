// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.components.ComponentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/6/13
 */
public abstract class DisposeAwareProjectChange implements Runnable {
  private final ComponentManager myComponentManager;

  protected DisposeAwareProjectChange(@NotNull ComponentManager componentManager) {
    myComponentManager = componentManager;
  }

  public abstract void execute();

  @Override
  public final void run() {
    if (!myComponentManager.isDisposed()) {
      execute();
    }
  }
}
