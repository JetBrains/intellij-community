/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnActionEvent;

public interface TreeExpander {
  default boolean isVisible(AnActionEvent event) {
    return true;
  }

  default void expandAll() {
  }

  boolean canExpand();

  void collapseAll();

  boolean canCollapse();
}
