package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.ApiStatus;

/**
 * This interface allows to specify if the tree action is enabled by default
 */
@ApiStatus.Internal
public interface TreeActionWithDefaultState extends TreeAction {
  boolean isEnabledByDefault();
}
