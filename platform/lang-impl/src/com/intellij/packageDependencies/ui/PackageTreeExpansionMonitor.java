// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.packageDependencies.ui;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

public final class PackageTreeExpansionMonitor {
  private PackageTreeExpansionMonitor() {
  }

  public static TreeExpansionMonitor<PackageDependenciesNode> install(final JTree tree) {
    return new TreeExpansionMonitor<>(tree) {
      @Override
      protected TreePath findPathByNode(final PackageDependenciesNode node) {
        if (node.getPsiElement() == null) {
          return new TreePath(node.getPath());
        }
        Enumeration<TreeNode> enumeration = ((DefaultMutableTreeNode)tree.getModel().getRoot()).breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
          final TreeNode nextElement = enumeration.nextElement();
          if (nextElement instanceof PackageDependenciesNode child) { //do not include root
            if (child.equals(node)) {
              return new TreePath(child.getPath());
            }
          }
        }
        return null;
      }
    };
  }
}