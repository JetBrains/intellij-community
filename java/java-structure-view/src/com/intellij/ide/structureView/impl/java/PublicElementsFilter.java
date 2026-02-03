// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PublicElementsFilter implements Filter{
  public static final @NonNls String ID = "SHOW_NON_PUBLIC";

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof JavaClassTreeElementBase) {
      return ((JavaClassTreeElementBase<?>)treeNode).isPublic();
    }
    else {
      return true;
    }
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(JavaStructureViewBundle.message("action.structureview.show.non.public"), null,
                                      IconManager.getInstance().getPlatformIcon(PlatformIcons.Private));
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }

  @Override
  public boolean isReverted() {
    return true;
  }
}
