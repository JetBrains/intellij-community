// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FieldsFilter implements Filter {
  public static final @NonNls String ID = "SHOW_FIELDS";

  @Override
  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof PsiFieldTreeElement);
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(StructureViewBundle.message("action.structureview.show.fields"), null,
                                      IconManager.getInstance().getPlatformIcon(PlatformIcons.Field));
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
