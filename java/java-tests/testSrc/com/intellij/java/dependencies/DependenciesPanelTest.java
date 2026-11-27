/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static com.intellij.projectView.ProjectViewFixture.sortClassesByName;
import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class DependenciesPanelTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("dependencies", "dependencies", JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testFiles() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    PsiFile file = ReadAction.computeBlocking(() -> {
      PsiDirectory psiDirectory = fixture.getPackageDirectory("com/package1");
      assertNotNull(psiDirectory);
      PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      assertNotNull(psiPackage);
      PsiClass[] classes = psiPackage.getClasses();
      sortClassesByName(classes);
      return classes[0].getContainingFile();
    });

    DependencyUISettings.getInstance().SCOPE_TYPE = PackagePatternProvider.PACKAGES;
    doTestDependenciesTrees(new AnalysisScope(file), """
                              -Root
                               Library Classes
                               -Production Classes
                                -com.package1
                                 [Class1.java]
                               Test Classes
                              """,
                            """
                              -Root
                               Library Classes
                               -Production Classes
                                -com.package1
                                 Class2.java
                               Test Classes
                              """);
  }

  @Test
  public void testModuleGroups() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ModifiableModuleModel model = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
    model.setModuleGroupPath(fixture.getModule(), new String[] {"a", "b"});
    model.renameModule(fixture.getModule(), "module");
    WriteAction.runAndWait(model::commit);
    fixture.createModule("util"); // groups aren't shown for single-module projects so we need to add an empty second module
    DependencyUISettings settings = DependencyUISettings.getInstance();
    settings.UI_GROUP_BY_SCOPE_TYPE = false;
    settings.UI_SHOW_FILES = false;
    settings.SCOPE_TYPE = ProjectPatternProvider.FILE;
    doTestDependenciesTrees(new AnalysisScope(fixture.getModule()), """
                              -Root
                               -[a]
                                -b
                                 -module
                                  -dependencies
                                   -src
                                    com/package1
                              """,
                            """
                              -Root
                               -External Dependencies
                                -< java 1.7 >
                                 -src.zip
                                  -java/lang
                                   String.java""");
  }

  private void doTestDependenciesTrees(AnalysisScope scope, String expectedLeftTree, String expectedRightTree) {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      DependenciesBuilder builder = new ForwardDependenciesBuilder(fixture.getProject(), scope);
      builder.analyze();

      DependenciesPanel dependenciesPanel = new DependenciesPanel(fixture.getProject(), builder);
      try {
        JTree leftTree = dependenciesPanel.getLeftTree();
        PlatformTestUtil.expandAll(leftTree);
        PlatformTestUtil.assertTreeEqual(leftTree, expectedLeftTree, true);

        JTree rightTree = dependenciesPanel.getRightTree();
        PlatformTestUtil.expandAll(rightTree);
        PlatformTestUtil.assertTreeEqual(rightTree, expectedRightTree, true);
      }
      finally {
        Disposer.dispose(dependenciesPanel);
      }
    });
  }

}
