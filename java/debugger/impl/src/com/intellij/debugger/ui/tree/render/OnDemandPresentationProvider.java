// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.NotNull;

public interface OnDemandPresentationProvider {
  void setPresentation(final @NotNull XValueNode node);
}
