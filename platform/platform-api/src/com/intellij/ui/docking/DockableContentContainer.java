// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.docking;

import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public interface DockableContentContainer {
  void add(@Nullable RelativePoint dropTarget);
}