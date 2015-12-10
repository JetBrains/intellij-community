/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Action for sorting items in a generic tree.
 *
 * @see com.intellij.ide.util.treeView.smartTree.TreeModel#getSorters()
 */
public interface Sorter extends TreeAction {
  Sorter[] EMPTY_ARRAY = new Sorter[0];

  /**
   * Returns the comparator used for comparing nodes in the tree.
   *
   * @return the comparator for comparing nodes.
   */
  Comparator getComparator();

  boolean isVisible();

  @NonNls String ALPHA_SORTER_ID = "ALPHA_COMPARATOR";

  /**
   * The default sorter which sorts the tree nodes alphabetically.
   */
  Sorter ALPHA_SORTER = new Sorter() {
    @Override
    public Comparator getComparator() {
      return new Comparator() {
        @Override
        public int compare(Object o1, Object o2) {
          String s1 = SorterUtil.getStringPresentation(o1);
          String s2 = SorterUtil.getStringPresentation(o2);
          return s1.compareToIgnoreCase(s2);
        }
      };
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    public String toString() {
      return getName();
    }

    @Override
    @NotNull
    public ActionPresentation getPresentation() {
      return new ActionPresentationData(IdeBundle.message("action.sort.alphabetically"),
                                        IdeBundle.message("action.sort.alphabetically"),
                                        AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    @NotNull
    public String getName() {
      return ALPHA_SORTER_ID;
    }
  };
}
