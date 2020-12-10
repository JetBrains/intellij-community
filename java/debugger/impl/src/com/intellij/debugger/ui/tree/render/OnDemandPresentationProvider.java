// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.NotNull;

public interface OnDemandPresentationProvider {
  void setPresentation(@NotNull final XValueNode node);
}
