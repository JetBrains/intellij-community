// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class DiscoveredTestsTree extends Tree {
  private final DiscoveredTestsTreeModel myModel;

  public DiscoveredTestsTree(String title) {
    myModel = new DiscoveredTestsTreeModel();
    setModel(new AsyncTreeModel(myModel));
    HintUpdateSupply.installSimpleHintUpdateSupply(this);
    getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
    getEmptyText().setText("No tests captured for " + title);
    setPaintBusy(true);
    setRootVisible(false);
    setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof PsiMember) {
          PsiMember psi = (PsiMember)value;
          setIcon(psi.getIcon(Iconable.ICON_FLAG_READ_STATUS));
          append(psi.getName());
        }
      }
    });
  }

  public synchronized void addTest(@NotNull PsiClass testClass,
                      @NotNull PsiMethod testMethod) {
    myModel.addTest(testClass, testMethod);
  }

  public synchronized List<Module> getContainingModules() {
    return myModel.getTestClasses().stream()
         .map(element -> ModuleUtilCore.findModuleForPsiElement(element))
         .filter(module -> module != null)
         .collect(Collectors.toList());
  }

  public synchronized PsiMethod[] getTestMethods() {
    return myModel.getTestMethods();
  }

  public PsiElement getSelectedElement() {
    return (PsiElement)getSelectionModel().getSelectionPath().getLastPathComponent();
  }

  public int getTestCount() {
    return myModel.getTestCount();
  }
}
