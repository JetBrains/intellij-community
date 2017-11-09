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
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;

public class PackagesTreeStructureTest extends TestSourceBasedTestCase {
  public void testPackageView() {
    ModuleManagerImpl.getInstanceImpl(myProject).setModuleGroupPath(myModule, new String[]{"Group"});
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
             "-Project\n" +
                       " -Module\n" +
                       "  -PsiPackage: com.package1\n" +
                       "   Class1.java\n" +
                       "   Class2.java\n" +
                       "   Class4.java\n" +
                       "   emptyClassFile.class\n" +
                       "   Form1.form\n" +
                       "   Form1.java\n" +
                       "   Form2.form\n" +
                       "  PsiPackage: empty\n" +
                       "  -PsiPackage: java\n" +
                       "   Class1.java\n" +
                       "  -PsiPackage: javax.servlet\n" +
                       "   Class1.java\n" +
                       "  -Libraries\n" +
                       "   -PsiPackage: java\n" +
                       "    +PsiPackage: awt\n" +
                       "    +PsiPackage: beans.beancontext\n" +
                       "    +PsiPackage: io\n" +
                       "    +PsiPackage: lang\n" +
                       "    +PsiPackage: net\n" +
                       "    +PsiPackage: rmi\n" +
                       "    +PsiPackage: security\n" +
                       "    +PsiPackage: sql\n" +
                       "    +PsiPackage: util\n" +
                       "   -PsiPackage: javax.swing\n" +
                       "    +PsiPackage: table\n" +
                       "    AbstractButton.class\n" +
                       "    Icon.class\n" +
                       "    JButton.class\n" +
                       "    JComponent.class\n" +
                       "    JDialog.class\n" +
                       "    JFrame.class\n" +
                       "    JLabel.class\n" +
                       "    JPanel.class\n" +
                       "    JScrollPane.class\n" +
                       "    JTable.class\n" +
                       "    SwingConstants.class\n" +
                       "    SwingUtilities.class\n" +
                       "   -PsiPackage: META-INF\n" +
                       "    MANIFEST.MF\n" +
                       "    MANIFEST.MF\n" +
                       "   -PsiPackage: org\n" +
                       "    +PsiPackage: intellij.lang.annotations\n" +
                       "    +PsiPackage: jetbrains.annotations\n" +
                       "   LICENSE\n" +
                       ""
      , 4);

    doTest(false, true,
              "-Project\n" +
                        " -PsiPackage: com.package1\n" +
                        "  Class1.java\n" +
                        "  Class2.java\n" +
                        "  Class4.java\n" +
                        "  emptyClassFile.class\n" +
                        "  Form1.form\n" +
                        "  Form1.java\n" +
                        "  Form2.form\n" +
                        " PsiPackage: empty\n" +
                        " -PsiPackage: java\n" +
                        "  Class1.java\n" +
                        " -PsiPackage: javax.servlet\n" +
                        "  Class1.java\n" +
                        " -Libraries\n" +
                        "  -PsiPackage: java\n" +
                        "   +PsiPackage: awt\n" +
                        "   +PsiPackage: beans.beancontext\n" +
                        "   +PsiPackage: io\n" +
                        "   +PsiPackage: lang\n" +
                        "   +PsiPackage: net\n" +
                        "   +PsiPackage: rmi\n" +
                        "   +PsiPackage: security\n" +
                        "   +PsiPackage: sql\n" +
                        "   +PsiPackage: util\n" +
                        "  -PsiPackage: javax.swing\n" +
                        "   +PsiPackage: table\n" +
                        "   AbstractButton.class\n" +
                        "   Icon.class\n" +
                        "   JButton.class\n" +
                        "   JComponent.class\n" +
                        "   JDialog.class\n" +
                        "   JFrame.class\n" +
                        "   JLabel.class\n" +
                        "   JPanel.class\n" +
                        "   JScrollPane.class\n" +
                        "   JTable.class\n" +
                        "   SwingConstants.class\n" +
                        "   SwingUtilities.class\n" +
                        "  -PsiPackage: META-INF\n" +
                        "   MANIFEST.MF\n" +
                        "   MANIFEST.MF\n" +
                        "  -PsiPackage: org\n" +
                        "   +PsiPackage: intellij.lang.annotations\n" +
                        "   +PsiPackage: jetbrains.annotations\n" +
                        "  LICENSE\n"
      , 3);

    doTest(true, false,
          "-Project\n" +
                    " -Module\n" +
                    "  -PsiPackage: com.package1\n" +
                    "   Class1.java\n" +
                    "   Class2.java\n" +
                    "   Class4.java\n" +
                    "   emptyClassFile.class\n" +
                    "   Form1.form\n" +
                    "   Form1.java\n" +
                    "   Form2.form\n" +
                    "  PsiPackage: empty\n" +
                    "  -PsiPackage: java\n" +
                    "   Class1.java\n" +
                    "  -PsiPackage: javax.servlet\n" +
                    "   Class1.java\n", 3);

    doTest(false, false, true, true, "-Project\n" +
                     " -PsiPackage: com.package1\n" +
                     "  Class1.java\n" +
                     "  Class2.java\n" +
                     "  Class4.java\n" +
                     "  emptyClassFile.class\n" +
                     "  Form1.form\n" +
                     "  Form1.java\n" +
                     "  Form2.form\n" +
                     " PsiPackage: empty\n" +
                     " -PsiPackage: java\n" +
                     "  Class1.java\n" +
                     " -PsiPackage: j.servlet\n" +
                     "  Class1.java\n", 3);

    doTest(false, false, "-Project\n" +
                     " -PsiPackage: com.package1\n" +
                     "  Class1.java\n" +
                     "  Class2.java\n" +
                     "  Class4.java\n" +
                     "  emptyClassFile.class\n" +
                     "  Form1.form\n" +
                     "  Form1.java\n" +
                     "  Form2.form\n" +
                     " PsiPackage: empty\n" +
                     " -PsiPackage: java\n" +
                     "  Class1.java\n" +
                     " -PsiPackage: javax.servlet\n" +
                     "  Class1.java\n", 3);
  }

  private void doTest(final boolean showModules, final boolean showLibraryContents, @NonNls final String expected, final int levels) {
    doTest(showModules, showLibraryContents, false, false, expected, levels);
  }

  private void doTest(final boolean showModules, final boolean showLibraryContents, boolean flattenPackages, boolean abbreviatePackageNames, @NonNls final String expected, final int levels) {
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);

    projectView.setShowModules(showModules, PackageViewPane.ID);

    projectView.setShowLibraryContents(showLibraryContents, PackageViewPane.ID);

    projectView.setFlattenPackages(flattenPackages, PackageViewPane.ID);
    projectView.setAbbreviatePackageNames(abbreviatePackageNames, PackageViewPane.ID);
    projectView.setHideEmptyPackages(true, PackageViewPane.ID);

    PackageViewPane packageViewPane = new PackageViewPane(myProject) {
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
