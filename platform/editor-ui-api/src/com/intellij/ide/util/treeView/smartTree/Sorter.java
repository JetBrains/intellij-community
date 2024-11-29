// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.PlatformEditorBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Action for sorting items in a generic tree.
 *
 * @see TreeModel#getSorters()
 */
public interface Sorter extends TreeAction {
  Sorter[] EMPTY_ARRAY = new Sorter[0];

  static @NonNls @NotNull String getAlphaSorterId() {
    return "ALPHA_COMPARATOR";
  }

  /**
   * Returns the comparator used for comparing nodes in the tree.
   *
   * @return the comparator for comparing nodes.
   */
  Comparator getComparator();

  boolean isVisible();

  /**
   * The default sorter which sorts the tree nodes alphabetically.
   */
  Sorter ALPHA_SORTER = new Sorter() {
    @Override
    public Comparator getComparator() {
      return (o1, o2) -> {
        String s1 = SorterUtil.getStringPresentation(o1);
        String s2 = SorterUtil.getStringPresentation(o2);
        return s1.compareToIgnoreCase(s2);
      };
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    public @NonNls String toString() {
      return getName();
    }

    @Override
    public @NotNull ActionPresentation getPresentation() {
      return new ActionPresentationData(PlatformEditorBundle.message("action.sort.alphabetically"),
                                        null,
                                        AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public @NotNull String getName() {
      return getAlphaSorterId();
    }
  };
}
