package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;

public interface NodeDescriptorProvidingKey {
  @NotNull
  Object getKey();
}
