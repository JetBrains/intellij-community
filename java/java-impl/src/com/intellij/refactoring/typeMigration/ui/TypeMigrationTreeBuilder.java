/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

/**
 * @author anna
 * Date: 11-Apr-2008
 */
public class TypeMigrationTreeBuilder extends AbstractTreeBuilder{
  public TypeMigrationTreeBuilder(JTree tree, Project project) {
    super(tree, (DefaultTreeModel)tree.getModel(), new TypeMigrationTreeStructure(project), AlphaComparator.INSTANCE, false);
    initRootNode();
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return false;
  }

  public void setRoot(MigrationRootNode root) {
    ((TypeMigrationTreeStructure)getTreeStructure()).setRoots(root);
  }
}
