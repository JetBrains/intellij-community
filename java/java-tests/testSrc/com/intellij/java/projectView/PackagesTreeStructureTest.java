// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import java.io.IOException;

import static com.intellij.projectView.ProjectViewFixture.checkContainsMethod;
import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class PackagesTreeStructureTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("projectView");

  @Test
  public void testPackageView() {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    ModifiableModuleModel moduleModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
    moduleModel.setModuleGroupPath(fixture.getModule(), new String[]{"Group"});
    WriteAction.runAndWait(() -> moduleModel.commit());

    final VirtualFile srcFile = fixture.getSrcDirectory().getVirtualFile();
    if (srcFile.findChild("empty") == null){
      WriteAction.runAndWait(() -> {
        try {
          srcFile.createChildDirectory(PackagesTreeStructureTest.class, "empty");
        }
        catch (IOException e) {
          fail(e.getLocalizedMessage());
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
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(fixture.getProject());

      projectView.setShowModules(PackageViewPane.ID, showModules);

      projectView.setShowLibraryContents(PackageViewPane.ID, showLibraryContents);

      projectView.setFlattenPackages(PackageViewPane.ID, flattenPackages);
      projectView.setAbbreviatePackageNames(PackageViewPane.ID, abbreviatePackageNames);
      projectView.setHideEmptyPackages(PackageViewPane.ID, true);

      PackageViewPane packageViewPane = new PackageViewPane(fixture.getProject()) {
        @NotNull
        @Override
        protected ProjectAbstractTreeStructureBase createStructure() {
          ProjectAbstractTreeStructureBase structure = super.createStructure();
          structure.setProviders(new ResourceBundleGrouper(fixture.getProject()));
          return structure;
        }
      };
      packageViewPane.createComponent();
      JTree tree = packageViewPane.getTree();
      PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree, levels));
      PlatformTestUtil.assertTreeEqual(tree, expected);
      checkContainsMethod(packageViewPane.getTreeStructure().getRootElement(), packageViewPane.getTreeStructure());
      Disposer.dispose(packageViewPane);
    });
  }
}
