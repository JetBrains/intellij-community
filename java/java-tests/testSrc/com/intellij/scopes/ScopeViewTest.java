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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
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
    PlatformTestUtil.expandAll(tree);
   PlatformTestUtil.assertTreeEqual(tree,
                                    """
                                      -Root
                                       -structure
                                        -src
                                         -package1
                                          -package2
                                           -package3
                                            Test3.java
                                          Test1.java
                                         Test.java
                                      """);

   panelSettings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;
   model = FileTreeModelBuilder.createTreeModel(getProject(), false, set, ALL_MARKED, panelSettings);
   tree.setModel(model);
   PlatformTestUtil.expandAll(tree);
   TreeUtil.traverse((TreeNode)model.getRoot(), node -> {
     ((DefaultMutableTreeNode)node).setUserObject(node.toString());
     return true;
   });
   PlatformTestUtil.assertTreeEqual(tree,
                                    """
                                      -Root
                                       -structure
                                        -src
                                         -package1
                                          -package2/package3
                                           Test3.java
                                          Test1.java
                                         Test.java
                                      """);

   panelSettings.UI_FLATTEN_PACKAGES = true;
   model = FileTreeModelBuilder.createTreeModel(getProject(), false, set, ALL_MARKED, panelSettings);
   tree.setModel(model);
    PlatformTestUtil.expandAll(tree);
   PlatformTestUtil.assertTreeEqual(tree,
                                    """
                                      -Root
                                       -structure
                                        -src
                                         -package1
                                          Test1.java
                                         -package1/package2/package3
                                          Test3.java
                                         Test.java
                                      """);
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
    PlatformTestUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, """
      -Root
       -a
        -b
         -module
          -structure
           -src
            -package1
             Test1.java
      """);
  }

  public void testExternalDependencies() {
    final Set<PsiFile> set = new HashSet<>();
    ContainerUtil.addAll(set, getPackageDirectory("package1").getFiles());
    set.add(JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(getProject())).getContainingFile());
    
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
    PlatformTestUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree,
                                     """
                                       -Root
                                        -structure
                                         -src
                                          -package1
                                           Test1.java
                                        -External Dependencies
                                         -< java 1.7 >
                                          -rt.jar
                                           -java
                                            -lang
                                             Object.class""");
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
