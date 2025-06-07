// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

public class VisibilitySorter implements Sorter{

  public static final Sorter INSTANCE = new VisibilitySorter();

  private static final ActionPresentation PRESENTATION = new ActionPresentation() {
    @Override
    public @NotNull String getText() {
      return StructureViewBundle.message("action.structureview.sort.by.visibility");
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public Icon getIcon() {
      return AllIcons.ObjectBrowser.VisibilitySort;
    }
  };
  public static final @NonNls String ID = "VISIBILITY_SORTER";

  @Override
  public @NotNull Comparator getComparator() {
    return VisibilityComparator.INSTANCE;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return PRESENTATION;
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }
}
