// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.psi.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.FontUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class DiscoveredTestsTree extends Tree implements UiDataProvider, Disposable {
  private final DiscoveredTestsTreeModel myModel;

  DiscoveredTestsTree(String title) {
    myModel = new DiscoveredTestsTreeModel();
    setModel(new AsyncTreeModel(myModel, this));
    HintUpdateSupply.installHintUpdateSupply(this, DiscoveredTestsTree::obj2psi);
    TreeUIHelper.getInstance().installTreeSpeedSearch(this, o -> {
      Object component = obj2psi(o.getLastPathComponent());
      return component instanceof PsiMember ? ((PsiMember)component).getName() : null;
    }, true);
    getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
    getEmptyText().setText(ExecutionBundle.message("no.tests.captured.for.0", title));
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
        if (value instanceof DiscoveredTestsTreeModel.Node node) {
          setIcon(node.getIcon());
          String name = node.getName();
          assert name != null;
          append(name);
          if (node instanceof DiscoveredTestsTreeModel.Node.Clazz) {
            String packageName = ((DiscoveredTestsTreeModel.Node.Clazz)node).getPackageName();
            if (packageName != null) {
              append(FontUtil.spaceAndThinSpace() + packageName, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            int testMethodCount = myModel.getChildren(value).size();
            append(JavaCompilerBundle.message("affected.tests.counts", testMethodCount, testMethodCount == 1 ? 0 : 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          else if (node instanceof DiscoveredTestsTreeModel.Node.Method) {
            boolean isParametrized = !((DiscoveredTestsTreeModel.Node.Method)node).getParameters().isEmpty();
            if (isParametrized) {
              append(FontUtil.spaceAndThinSpace() + JavaCompilerBundle.message("test.discovery.parametrized"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
          }
          SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, false);
        }
      }
    });
    getModel().addTreeModelListener(new TreeModelAdapter() {
      //TODO
      boolean myAlreadyDone;
      @Override
      protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
        if (!myAlreadyDone && getTestCount() != 0) {
          myAlreadyDone = true;
          EdtInvocationManager.getInstance().invokeLater(() -> {
            TreeUtil.collapseAll(DiscoveredTestsTree.this, 0);
            TreeUtil.promiseSelectFirst(DiscoveredTestsTree.this);
          });
        }
      }
    });
    DefaultTreeExpander treeExpander = new DefaultTreeExpander(this);
    CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this);
    CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this);
  }

  @Override
  public void dispose() {
  }

  public void addTest(@NotNull PsiClass testClass,
                      @Nullable PsiMethod testMethod,
                      @Nullable String parameter) {
    myModel.addTest(testClass, testMethod, parameter);
  }

  public @NotNull Set<Module> getContainingModules() {
    return myModel.getTestClasses().stream()
                  .map(element -> {
                    SmartPsiElementPointer<PsiClass> pointer = element.getPointer();
                    return ModuleUtilCore.findModuleForFile(pointer.getVirtualFile(), pointer.getProject());
                  })
                  .filter(Objects::nonNull)
                  .collect(Collectors.toSet());
  }

  TestMethodUsage @NotNull [] getTestMethods() {
    return myModel.getTestMethods();
  }

  public @Nullable PsiElement getSelectedElement() {
    TreePath path = getSelectionModel().getSelectionPath();
    return obj2psi(path == null ? null : path.getLastPathComponent());
  }

  private static @Nullable PsiElement obj2psi(@Nullable Object obj) {
    return Optional.ofNullable(ObjectUtils.tryCast(obj, DiscoveredTestsTreeModel.Node.class))
                   .map(n -> n.getPointer())
                   .map(p -> p.getElement())
                   .orElse(null);
  }

  public int getTestCount() {
    return myModel.getTestCount();
  }

  public int getTestClassesCount() {
    return myModel.getTestClassesCount();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    TreePath[] paths = getSelectionPaths();
    sink.set(LangDataKeys.POSITION_ADJUSTER_POPUP, PopupUtil.getPopupContainerFor(this));

    if (paths == null || paths.length == 0) return;
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      return obj2psi(paths[0].getLastPathComponent());
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      List<PsiElement> result = new SmartList<>();
      TreeModel model = getModel();
      for (TreePath p : paths) {
        Object o = p.getLastPathComponent();
        PsiElement e = obj2psi(o);
        if (e instanceof PsiMethod) {
          result.add(e);
        }
        else {
          int count = model.getChildCount(e);
          if (count == 0 && e != null) {
            result.add(e);
          }
          else {
            for (int i = 0; i < count; i++) {
              ContainerUtil.addIfNotNull(result, ObjectUtils.tryCast(obj2psi(model.getChild(e, i)), PsiMethod.class));
            }
          }
        }
      }
      return result.toArray(PsiElement.EMPTY_ARRAY);
    });
  }
}
