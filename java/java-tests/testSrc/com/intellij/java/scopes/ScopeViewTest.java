// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.scopes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.packageDependencies.ui.FileTreeModelBuilder;
import com.intellij.packageDependencies.ui.Marker;
import com.intellij.packageDependencies.ui.TreeModel;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;

@TestApplication
public class ScopeViewTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("packageSet", "structure");

  private static final Marker ALL_MARKED = new Marker() {
    @Override
    public boolean isMarked(@NotNull VirtualFile file) {
      return true;
    }
  };

  @Test
  public void testFiles() {
   TestSourceBasedFixture fixture = testSourceBasedFixture.get();
   final Set<PsiFile> set = new HashSet<>();
   ReadAction.runBlocking(() -> {
     ContainerUtil.addAll(set, fixture.getPackageDirectory("package1").getFiles());
     ContainerUtil.addAll(set, fixture.getPackageDirectory("package1/package2/package3").getFiles());
     ContainerUtil.addAll(set, fixture.getPackageDirectory(".").getFiles());
   });
   final DependenciesPanel.DependencyPanelSettings panelSettings = new DependenciesPanel.DependencyPanelSettings();
   panelSettings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = false;
   panelSettings.UI_FLATTEN_PACKAGES = false;
   panelSettings.UI_SHOW_FILES = true;
   panelSettings.UI_FILTER_LEGALS = false;
   panelSettings.UI_SHOW_MODULES = true;
   panelSettings.UI_GROUP_BY_SCOPE_TYPE = false;
   EdtTestUtil.runInEdtAndWait(() -> {
     TreeModel model = FileTreeModelBuilder.createTreeModel(fixture.getProject(), false, set, ALL_MARKED, panelSettings);

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
     model = FileTreeModelBuilder.createTreeModel(fixture.getProject(), false, set, ALL_MARKED, panelSettings);
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
     model = FileTreeModelBuilder.createTreeModel(fixture.getProject(), false, set, ALL_MARKED, panelSettings);
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
   });
  }

  @Test
  public void testModuleGroups() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
    moduleModel.setModuleGroupPath(fixture.getModule(), new String[]{"a", "b"});
    moduleModel.renameModule(fixture.getModule(), "module");
    WriteAction.runAndWait(moduleModel::commit);
    fixture.createModule("util"); // groups aren't shown for single-module projects so we need to add an empty second module

    Set<PsiFile> files = ReadAction.computeBlocking(() -> Set.of(fixture.getPackageDirectory("package1").getFiles()));
    EdtTestUtil.runInEdtAndWait(() -> {
      DependenciesPanel.DependencyPanelSettings settings = new DependenciesPanel.DependencyPanelSettings();
      settings.UI_GROUP_BY_SCOPE_TYPE = false;
      TreeModel model = FileTreeModelBuilder.createTreeModel(fixture.getProject(), false, files, ALL_MARKED, settings);
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
    });
  }

  @Test
  public void testExternalDependencies() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    final Set<PsiFile> set = new HashSet<>();
    ReadAction.runBlocking(() -> {
      ContainerUtil.addAll(set, fixture.getPackageDirectory("package1").getFiles());
      set.add(JavaPsiFacade.getInstance(fixture.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(fixture.getProject())).getContainingFile());
    });
    
    final DependenciesPanel.DependencyPanelSettings panelSettings = new DependenciesPanel.DependencyPanelSettings();
    panelSettings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = false;
    panelSettings.UI_FLATTEN_PACKAGES = false;
    panelSettings.UI_SHOW_FILES = true;
    panelSettings.UI_FILTER_LEGALS = false;
    panelSettings.UI_SHOW_MODULES = true;
    panelSettings.UI_GROUP_BY_SCOPE_TYPE = false;

    
    EdtTestUtil.runInEdtAndWait(() -> {
      TreeModel model = FileTreeModelBuilder.createTreeModel(fixture.getProject(), false, set, ALL_MARKED, panelSettings);

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
    });
  }
}
