// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

public class LoadingNode extends DefaultMutableTreeNode {
  public LoadingNode() {
    this(getText());
  }

  public static @NotNull @Nls String getText() {
    return IdeBundle.message("treenode.loading");
  }

  public LoadingNode(@Nls @NotNull String text) {
    super(text);
  }
}