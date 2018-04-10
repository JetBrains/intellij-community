// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.impl.DebuggerSession;

import java.util.List;

/**
 * Defines contract for callback to listen hot swap status.
 */
public interface HotSwapStatusListener {
  default void onCancel(List<DebuggerSession> sessions) {
  }

  default void onSuccess(List<DebuggerSession> sessions) {
  }

  default void onFailure(List<DebuggerSession> sessions) {
  }
}
