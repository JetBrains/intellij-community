// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public interface PagePluginLayout {
  int getPageCount(@NotNull JComponent parent);
}