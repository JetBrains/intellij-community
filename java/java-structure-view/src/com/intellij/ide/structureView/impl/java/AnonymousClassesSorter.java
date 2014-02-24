/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.SorterUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class AnonymousClassesSorter implements Sorter {
  public static Sorter INSTANCE = new AnonymousClassesSorter();

  private final Comparator myComparator = new Comparator() {
    @Override
    public int compare(Object o1, Object o2) {
      String s1 = SorterUtil.getStringPresentation(o1);
      String s2 = SorterUtil.getStringPresentation(o2);
      if (s1.startsWith("$") && s2.startsWith("$")) {
        try {
          return Integer.parseInt(s1.substring(1)) - Integer.parseInt(s2.substring(1));
        } catch (NumberFormatException e) {//
        }
      }
      return 0;
    }
  };
  
  @NotNull
  @Override
  public Comparator getComparator() {
    return myComparator;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return ALPHA_SORTER.getPresentation();
  }

  @NotNull
  @Override
  public String getName() {
    return "ANONYMOUS_CLASSES_SORTER";
  }
}
