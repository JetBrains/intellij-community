// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.scopes;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.packageDependencies.ui.FileTreeModelBuilder;
import com.intellij.packageDependencies.ui.Marker;
import com.intellij.packageDependencies.ui.TreeModel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

public class ScopeViewTest extends TestSourceBasedTestCase {
  private static final Marker ALL_MARKED = new Marker() {
    @Override
    public boolean isMarked(@NotNull VirtualFile file) {
      return true;
    }
  };

  public void testFiles() {
   final Set<PsiFile> set = new HashSet<>();
   ContainerUtil.addAll(set, getPackageDirectory("package1").getFiles());
   ContainerUtil.addAll(set, getPackageDirectory("package1/package2/package3").getFiles());
   ContainerUtil.addAll(set, getPackageDirectory(".").getFiles());
   final DependenciesPanel.DependencyPanelSettings panelSettings = new DependenciesPanel.DependencyPanelSettings();
   panelSettings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = false;
   panelSettings.UI_FLATTEN_PACKAGES = false;
   panelSettings.UI_SHOW_FILES = true;
   panelSettings.UI_FILTER_LEGALS = false;
   panelSettings.UI_SHOW_MODULES = true;
   panelSettings.UI_GROUP_BY_SCOPE_TYPE = false;
   TreeModel model = FileTreeModelBuilder.createTreeModel(getProject(), false, set, ALL_MARKED, panelSettings);

   JTree tree = new Tree();
   TreeTestUtil.assertTreeUI(tree);
   tree.setModel(model);
   TreeUtil.expandAll(tree);
   PlatformTestUtil.assertTreeEqual(tree,
                                    "-Root\n" +
                                    " -structure\n" +
                                    "  -src\n" +
                                    "   -package1\n" +
                                    "    -package2\n" +
                                    "     -package3\n" +
                                    "      Test3.java\n" +
                                    "    Test1.java\n" +
                                    "   Test.java\n");

   panelSettings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;
   model = FileTreeModelBuilder.createTreeModel(getProject(), false, set, ALL_MARKED, panelSettings);
   tree.setModel(model);
   TreeUtil.expandAll(tree);
   TreeUtil.traverse((TreeNode)model.getRoot(), node -> {
     ((DefaultMutableTreeNode)node).setUserObject(node.toString());
     return true;
   });
   PlatformTestUtil.assertTreeEqual(tree,
                                    "-Root\n" +
                                    " -structure\n" +
                                    "  -src\n" +
                                    "   -package1\n" +
                                    "    -package2/package3\n" +
                                    "     Test3.java\n" +
                                    "    Test1.java\n" +
                                    "   Test.java\n");

   panelSettings.UI_FLATTEN_PACKAGES = true;
   model = FileTreeModelBuilder.createTreeModel(getProject(), false, set, ALL_MARKED, panelSettings);
   tree.setModel(model);
   TreeUtil.expandAll(tree);
   PlatformTestUtil.assertTreeEqual(tree,
                                    "-Root\n" +
                                    " -structure\n" +
                                    "  -src\n" +
                                    "   -package1\n" +
                                    "    Test1.java\n" +
                                    "   -package1/package2/package3\n" +
                                    "    Test3.java\n" +
                                    "   Test.java\n");
  }

  public void testModuleGroups() throws Exception {
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    moduleModel.setModuleGroupPath(myModule, new String[]{"a", "b"});
    moduleModel.renameModule(myModule, "module");
    WriteAction.run(moduleModel::commit);
    createModule("util"); // groups aren't shown for single-module projects so we need to add an empty second module

    Set<PsiFile> files = ContainerUtil.set(getPackageDirectory("package1").getFiles());
    DependenciesPanel.DependencyPanelSettings settings = new DependenciesPanel.DependencyPanelSettings();
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    TreeModel model = FileTreeModelBuilder.createTreeModel(getProject(), false, files, ALL_MARKED, settings);
    JTree tree = new Tree(model);
    TreeTestUtil.assertTreeUI(tree);
    TreeUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-Root\n" +
                                           " -a\n" +
                                           "  -b\n" +
                                           "   -module\n" +
                                           "    -structure\n" +
                                           "     -src\n" +
                                           "      -package1\n" +
                                           "       Test1.java\n");
  }

  @NotNull
  @Override
  protected String getTestDirectoryName() {
    return "structure";
  }

  @Override
  protected String getTestPath() {
    return "packageSet";
  }
}
