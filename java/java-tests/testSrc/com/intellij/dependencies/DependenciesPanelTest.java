/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import junit.framework.Assert;

import javax.swing.*;

public class DependenciesPanelTest extends TestSourceBasedTestCase{
  public void testDependencies(){
    DependenciesPanel dependenciesPanel = null;
    try {
      final PsiDirectory psiDirectory = getPackageDirectory("com/package1");
      Assert.assertNotNull(psiDirectory);
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      Assert.assertNotNull(psiPackage);
      final PsiClass[] classes = psiPackage.getClasses();
      sortClassesByName(classes);
      final PsiFile file = classes[0].getContainingFile();
      final AnalysisScope scope = new AnalysisScope(file);
      final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, scope);
      builder.analyze();
      DependencyUISettings.getInstance().SCOPE_TYPE = PackagePatternProvider.PACKAGES;
      dependenciesPanel = new DependenciesPanel(myProject, builder);
      JTree leftTree = dependenciesPanel.getLeftTree();
      IdeaTestUtil.assertTreeEqual(leftTree, "-Root\n" +
                            " Library Classes\n" +
                            " -Production Classes\n" +
                            "  -" + myModule.getName() + "\n" +
                            "   -com.package1\n" +
                            "    [Class1.java]\n" +
                                " Test Classes\n", true);

      JTree rightTree = dependenciesPanel.getRightTree();
      IdeaTestUtil.assertTreeEqual(rightTree, "-Root\n" +
                             " Library Classes\n" +
                             " -Production Classes\n" +
                             "  -" + myModule.getName() + "\n" +
                             "   -com.package1\n" +
                             "    Class2.java\n" +
                             " Test Classes\n", true);
    }
    finally {
      if (dependenciesPanel != null) {
        Disposer.dispose(dependenciesPanel);
      }
    }
  }

  protected String getTestPath() {
    return "dependencies";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}
