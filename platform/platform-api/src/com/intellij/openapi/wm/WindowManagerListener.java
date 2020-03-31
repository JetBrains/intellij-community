// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface WindowManagerListener extends EventListener {
  void frameCreated(@NotNull IdeFrame frame);

  void beforeFrameReleased(@NotNull IdeFrame frame);
}
