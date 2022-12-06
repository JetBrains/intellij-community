// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.impl.DebuggerSession;

import java.util.List;

/**
 * Each attempt at hotswapping some classes is either canceled, or it succeeds, or it fails.
 */
public interface HotSwapStatusListener {
  default void onCancel(List<DebuggerSession> sessions) {
  }

  default void onSuccess(List<DebuggerSession> sessions) {
  }

  default void onFailure(List<DebuggerSession> sessions) {
  }
}
