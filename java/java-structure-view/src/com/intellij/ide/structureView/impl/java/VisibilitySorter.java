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
    @NotNull
    public String getText() {
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
  @NonNls public static final String ID = "VISIBILITY_SORTER";

  @Override
  @NotNull
  public Comparator getComparator() {
    return VisibilityComparator.IMSTANCE;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  @NotNull
  public ActionPresentation getPresentation() {
    return PRESENTATION;
  }

  @Override
  @NotNull
  public String getName() {
    return ID;
  }
}
