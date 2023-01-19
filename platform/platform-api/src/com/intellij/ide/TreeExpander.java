// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

public interface TreeExpander {

  default void expandAll() {
  }

  boolean canExpand();

  default boolean isExpandAllVisible() {
    return true;
  }


  void collapseAll();

  boolean canCollapse();

  default boolean isCollapseAllVisible() {
    return true;
  }
}
