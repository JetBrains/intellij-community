// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.openapi.util.Iconable;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface MemberChooserObject extends Iconable, OptMultiSelector.OptElement {
  void renderTreeNode(SimpleColoredComponent component, JTree tree);

  @ApiStatus.Internal
  default @NotNull SimpleTextAttributes getAttributes() { return SimpleTextAttributes.REGULAR_ATTRIBUTES; }

  @ApiStatus.Internal
  default @NotNull SimpleTextAttributes getSecondaryTextAttributes() { return SimpleTextAttributes.GRAY_ATTRIBUTES; }

  @Override
  default @Nullable Icon getIcon(int flags) { return null; }

  @Override
  boolean equals(Object o);

  @Override
  int hashCode();
}
