// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public interface ConfigurableTreeRenderer {
  @Nullable
  Component getRightDecorator(@NotNull SimpleTree tree, @Nullable UnnamedConfigurable configurable, boolean selected);
}