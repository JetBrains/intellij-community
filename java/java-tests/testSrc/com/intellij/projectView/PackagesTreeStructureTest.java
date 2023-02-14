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
package com.intellij.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

public class PackagesTreeStructureTest extends TestSourceBasedTestCase {
  public void testPackageView() {
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    moduleModel.setModuleGroupPath(myModule, new String[]{"Group"});
    WriteAction.runAndWait(() -> moduleModel.commit());

    final VirtualFile srcFile = getSrcDirectory().getVirtualFile();
    if (srcFile.findChild("empty") == null){
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            srcFile.createChildDirectory(this, "empty");
          }
          catch (IOException e) {
            fail(e.getLocalizedMessage());
          }
        }
      });
    }

    doTest(true, true,
           """
             -Project
              -Module
               -PsiPackage: com.package1
                Class1.java
                Class2.java
                Class4.java
                emptyClassFile.class
                Form1.form
                Form1.java
                Form2.form
               PsiPackage: empty
               -PsiPackage: java
                Class1.java
               -PsiPackage: javax.servlet
                Class1.java
               -Libraries
                -PsiPackage: java
                 +PsiPackage: awt
                 +PsiPackage: beans.beancontext
                 +PsiPackage: io
                 +PsiPackage: lang
                 +PsiPackage: net
                 +PsiPackage: rmi
                 +PsiPackage: security
                 +PsiPackage: sql
                 +PsiPackage: util
                -PsiPackage: javax.swing
                 +PsiPackage: table
                 AbstractButton.class
                 Icon.class
                 JButton.class
                 JComponent.class
                 JDialog.class
                 JFrame.class
                 JLabel.class
                 JPanel.class
                 JScrollPane.class
                 JTable.class
                 SwingConstants.class
                 SwingUtilities.class
                -PsiPackage: META-INF
                 MANIFEST.MF
                 MANIFEST.MF
                -PsiPackage: org
                 +PsiPackage: intellij.lang.annotations
                 +PsiPackage: jetbrains.annotations
                LICENSE
             """
      , 4);

    doTest(false, true,
           """
             -Project
              -PsiPackage: com.package1
               Class1.java
               Class2.java
               Class4.java
               emptyClassFile.class
               Form1.form
               Form1.java
               Form2.form
              PsiPackage: empty
              -PsiPackage: java
               Class1.java
              -PsiPackage: javax.servlet
               Class1.java
              -Libraries
               -PsiPackage: java
                +PsiPackage: awt
                +PsiPackage: beans.beancontext
                +PsiPackage: io
                +PsiPackage: lang
                +PsiPackage: net
                +PsiPackage: rmi
                +PsiPackage: security
                +PsiPackage: sql
                +PsiPackage: util
               -PsiPackage: javax.swing
                +PsiPackage: table
                AbstractButton.class
                Icon.class
                JButton.class
                JComponent.class
                JDialog.class
                JFrame.class
                JLabel.class
                JPanel.class
                JScrollPane.class
                JTable.class
                SwingConstants.class
                SwingUtilities.class
               -PsiPackage: META-INF
                MANIFEST.MF
                MANIFEST.MF
               -PsiPackage: org
                +PsiPackage: intellij.lang.annotations
                +PsiPackage: jetbrains.annotations
               LICENSE
             """
      , 3);

    doTest(true, false,
           """
             -Project
              -Module
               -PsiPackage: com.package1
                Class1.java
                Class2.java
                Class4.java
                emptyClassFile.class
                Form1.form
                Form1.java
                Form2.form
               PsiPackage: empty
               -PsiPackage: java
                Class1.java
               -PsiPackage: javax.servlet
                Class1.java
             """, 3);

    doTest(false, false, true, true, """
      -Project
       -PsiPackage: com.package1
        Class1.java
        Class2.java
        Class4.java
        emptyClassFile.class
        Form1.form
        Form1.java
        Form2.form
       PsiPackage: empty
       -PsiPackage: java
        Class1.java
       -PsiPackage: j.servlet
        Class1.java
      """, 3);

    doTest(false, false, """
      -Project
       -PsiPackage: com.package1
        Class1.java
        Class2.java
        Class4.java
        emptyClassFile.class
        Form1.form
        Form1.java
        Form2.form
       PsiPackage: empty
       -PsiPackage: java
        Class1.java
       -PsiPackage: javax.servlet
        Class1.java
      """, 3);
  }

  private void doTest(final boolean showModules, final boolean showLibraryContents, @NonNls final String expected, final int levels) {
    doTest(showModules, showLibraryContents, false, false, expected, levels);
  }

  private void doTest(final boolean showModules, final boolean showLibraryContents, boolean flattenPackages, boolean abbreviatePackageNames, @NonNls final String expected, final int levels) {
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);

    projectView.setShowModules(PackageViewPane.ID, showModules);

    projectView.setShowLibraryContents(PackageViewPane.ID, showLibraryContents);

    projectView.setFlattenPackages(PackageViewPane.ID, flattenPackages);
    projectView.setAbbreviatePackageNames(PackageViewPane.ID, abbreviatePackageNames);
    projectView.setHideEmptyPackages(PackageViewPane.ID, true);

    PackageViewPane packageViewPane = new PackageViewPane(myProject) {
      @NotNull
      @Override
      protected ProjectAbstractTreeStructureBase createStructure() {
        ProjectAbstractTreeStructureBase structure = super.createStructure();
        structure.setProviders(new ResourceBundleGrouper(myProject));
        return structure;
      }
    };
    packageViewPane.createComponent();
    JTree tree = packageViewPane.getTree();
    PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree, levels));
    PlatformTestUtil.assertTreeEqual(tree, expected);
    BaseProjectViewTestCase.checkContainsMethod(packageViewPane.getTreeStructure().getRootElement(), packageViewPane.getTreeStructure());
    Disposer.dispose(packageViewPane);
  }

  @Override
  protected String getTestPath() {
    return "projectView";
  }
}
