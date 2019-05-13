// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.NamedRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * @author Sergey.Malenkov
 */
abstract class TreeRunnable extends NamedRunnable {
  TreeRunnable(@NotNull String name) {
    super(name);
  }

  protected abstract void perform();

  @Override
  public final void run() {
    trace("started");
    try {
      perform();
    }
    finally {
      trace("finished");
    }
  }

  abstract static class TreeConsumer<T> extends TreeRunnable implements Consumer<T> {
    TreeConsumer(@NotNull String name) {
      super(name);
    }

    @Override
    public final void accept(T t) {
      run();
    }
  }
}
