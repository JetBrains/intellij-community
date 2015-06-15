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
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.packageDependencies.ui.PackagePatternProvider;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;

import javax.swing.*;

public class DependenciesPanelTest extends TestSourceBasedTestCase {
  public void testDependencies() {
    PsiDirectory psiDirectory = getPackageDirectory("com/package1");
    assertNotNull(psiDirectory);
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    assertNotNull(psiPackage);
    PsiClass[] classes = psiPackage.getClasses();
    sortClassesByName(classes);
    PsiFile file = classes[0].getContainingFile();

    AnalysisScope scope = new AnalysisScope(file);
    DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, scope);
    builder.analyze();

    DependencyUISettings.getInstance().SCOPE_TYPE = PackagePatternProvider.PACKAGES;
    DependenciesPanel dependenciesPanel = new DependenciesPanel(myProject, builder);
    try {
      JTree leftTree = dependenciesPanel.getLeftTree();
      PlatformTestUtil.assertTreeEqual(leftTree, "-Root\n" +
                                                 " Library Classes\n" +
                                                 " -Production Classes\n" +
                                                 "  -com.package1\n" +
                                                 "   [Class1.java]\n" +
                                                 " Test Classes\n", true);

      JTree rightTree = dependenciesPanel.getRightTree();
      PlatformTestUtil.assertTreeEqual(rightTree, "-Root\n" +
                                                  " Library Classes\n" +
                                                  " -Production Classes\n" +
                                                  "  -com.package1\n" +
                                                  "   Class2.java\n" +
                                                  " Test Classes\n", true);
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
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
