/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.packageDependencies.ui.PackagePatternProvider;
import com.intellij.packageDependencies.ui.ProjectPatternProvider;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;

public class DependenciesPanelTest extends TestSourceBasedTestCase {
  public void testFiles() {
    PsiDirectory psiDirectory = getPackageDirectory("com/package1");
    assertNotNull(psiDirectory);
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    assertNotNull(psiPackage);
    PsiClass[] classes = psiPackage.getClasses();
    sortClassesByName(classes);
    PsiFile file = classes[0].getContainingFile();

    DependencyUISettings.getInstance().SCOPE_TYPE = PackagePatternProvider.PACKAGES;
    doTestDependenciesTrees(new AnalysisScope(file), "-Root\n" +
                                                     " Library Classes\n" +
                                                     " -Production Classes\n" +
                                                     "  -com.package1\n" +
                                                     "   [Class1.java]\n" +
                                                     " Test Classes\n",
                            "-Root\n" +
                            " Library Classes\n" +
                            " -Production Classes\n" +
                            "  -com.package1\n" +
                            "   Class2.java\n" +
                            " Test Classes\n");
  }

  public void testModuleGroups() throws Exception {
    ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
    model.setModuleGroupPath(myModule, new String[] {"a", "b"});
    model.renameModule(myModule, "module");
    WriteAction.run(model::commit);
    createModule("util"); // groups aren't shown for single-module projects so we need to add an empty second module
    DependencyUISettings settings = DependencyUISettings.getInstance();
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    settings.UI_SHOW_FILES = false;
    settings.SCOPE_TYPE = ProjectPatternProvider.FILE;
    doTestDependenciesTrees(new AnalysisScope(myModule), "-Root\n" +
                                                         " -[a]\n" +
                                                         "  -b\n" +
                                                         "   -module\n" +
                                                         "    -dependencies\n" +
                                                         "     -src\n" +
                                                         "      com/package1\n",
                            "Root\n");
  }

  private void doTestDependenciesTrees(AnalysisScope scope, String expectedLeftTree, String expectedRightTree) {
    DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, scope);
    builder.analyze();

    DependenciesPanel dependenciesPanel = new DependenciesPanel(myProject, builder);
    try {
      JTree leftTree = dependenciesPanel.getLeftTree();
      TreeUtil.expandAll(leftTree);
      PlatformTestUtil.assertTreeEqual(leftTree, expectedLeftTree, true);

      JTree rightTree = dependenciesPanel.getRightTree();
      TreeUtil.expandAll(rightTree);
      PlatformTestUtil.assertTreeEqual(rightTree, expectedRightTree, true);
    }
    finally {
      Disposer.dispose(dependenciesPanel);
    }
  }

  @Override
  protected String getTestPath() {
    return "dependencies";
  }

  @Override
  protected String getTestDirectoryName() {
    return "dependencies";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
