// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface MemberChooserObject {
  void renderTreeNode(SimpleColoredComponent component, JTree tree);

  @NlsContexts.Label @NotNull String getText();

  default @NotNull SimpleTextAttributes getAttributes() { return SimpleTextAttributes.REGULAR_ATTRIBUTES; }
  default @Nls @Nullable String getSecondaryText() { return null; }
  default @NotNull SimpleTextAttributes getSecondaryTextAttributes() { return SimpleTextAttributes.GRAY_ATTRIBUTES; }
  default @Nullable Icon getIcon() { return null; }

  @Override
  boolean equals(Object o);

  @Override
  int hashCode();
}
