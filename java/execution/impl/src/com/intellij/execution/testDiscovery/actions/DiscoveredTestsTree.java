// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.FontUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;
import java.util.stream.Collectors;

class DiscoveredTestsTree extends Tree implements DataProvider {
  private final DiscoveredTestsTreeModel myModel;

  public DiscoveredTestsTree(String title) {
    myModel = new DiscoveredTestsTreeModel();
    setModel(new AsyncTreeModel(myModel));
    HintUpdateSupply.installSimpleHintUpdateSupply(this);
    TreeUIHelper.getInstance().installTreeSpeedSearch(this, o -> {
      Object component = o.getLastPathComponent();
      return component instanceof PsiMember ? ((PsiMember)component).getName() : null;
    }, true);
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
          if (psi instanceof PsiClass) {
            String packageName = PsiUtil.getPackageName((PsiClass)psi);
            if (packageName != null) {
              append(FontUtil.thinSpace() + "(" + packageName + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            int testMethodCount = myModel.getChildren(psi).size();
            append(FontUtil.spaceAndThinSpace() + (testMethodCount != 1 ? (testMethodCount + " tests") : "1 test"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, false);
        }
      }
    });
    getModel().addTreeModelListener(new TreeModelAdapter() {
      //TODO
      boolean myAlreadyDone;
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        if (!myAlreadyDone && !myModel.getTestClasses().isEmpty()) {
          myAlreadyDone = true;
          EdtInvocationManager.getInstance().invokeLater(() -> TreeUtil.selectFirstNode(DiscoveredTestsTree.this));
        }
      }
    });
  }

  public synchronized void addTest(@NotNull PsiClass testClass,
                                   @NotNull PsiMethod testMethod) {
    myModel.addTest(testClass, testMethod);
  }

  public List<Module> getContainingModules() {
    return myModel.getTestClasses().stream()
                  .map(element -> ModuleUtilCore.findModuleForPsiElement(element))
                  .filter(module -> module != null)
                  .collect(Collectors.toList());
  }

  public PsiMethod[] getTestMethods() {
    return myModel.getTestMethods();
  }

  public PsiElement getSelectedElement() {
    TreePath path = getSelectionModel().getSelectionPath();
    return ObjectUtils.tryCast(path == null ? null : path.getLastPathComponent(), PsiElement.class);
  }

  public int getTestCount() {
    return myModel.getTestCount();
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return getSelectedElement();
    }
    else if (LangDataKeys.POSITION_ADJUSTER_POPUP.is(dataId)) {
      return PopupUtil.getPopupContainerFor(this);
    }
    return null;
  }
}
