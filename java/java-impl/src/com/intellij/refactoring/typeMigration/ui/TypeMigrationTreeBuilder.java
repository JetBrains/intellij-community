// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author anna
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

  @Nullable
  @Override
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
