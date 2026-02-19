// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.CustomPortServerManager;

public abstract class CustomPortServerManagerBase extends CustomPortServerManager {
  protected @Nullable CustomPortService manager;

  @Override
  public void setManager(@Nullable CustomPortService manager) {
    this.manager = manager;
  }

  public boolean isBound() {
    return manager != null && manager.isBound();
  }

  public void portChanged() {
    if (manager != null) {
      manager.rebind();
    }
  }
}