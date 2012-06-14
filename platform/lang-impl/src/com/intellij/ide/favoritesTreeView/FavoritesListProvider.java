/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import javax.swing.tree.TreeCellRenderer;
import java.util.Comparator;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/7/12
 * Time: 4:17 PM
 */
public interface FavoritesListProvider {
  ExtensionPointName<FavoritesListProvider> EP_NAME = new ExtensionPointName<FavoritesListProvider>("com.intellij.favoritesListProvider");

  String getListName(final Project project);
  boolean canBeRemoved();
  boolean isTreeLike();

  Comparator<FavoritesTreeNodeDescriptor> getNodeDescriptorComparator();

  Operation getCustomDeleteOperation();
  Operation getCustomAddOperation();
  Operation getCustomEditOperation();

  TreeCellRenderer getTreeCellRenderer();

  interface Operation {
    boolean willHandle(final DnDAwareTree tree);
    String getCustomName();
    void handle(Project project, final DnDAwareTree tree);
  }
}
