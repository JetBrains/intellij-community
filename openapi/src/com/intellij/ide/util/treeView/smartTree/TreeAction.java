package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.NotNull;

public interface TreeAction {
  @NotNull ActionPresentation getPresentation();
  @NotNull String getName();
}
