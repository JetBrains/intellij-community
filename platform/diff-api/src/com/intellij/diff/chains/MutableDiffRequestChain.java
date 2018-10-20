// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.chains;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

@ApiStatus.Experimental
public interface MutableDiffRequestChain extends DiffRequestChain {
  void addListener(@NotNull Listener listener, @NotNull Disposable disposable);

  /**
   * Invoked when corresponding DiffRequestProcessor is created / disposed.
   */
  @CalledInAwt
  default void onAssigned(boolean isAssigned) { }

  interface Listener extends EventListener {
    void onChainChange();
  }
}
