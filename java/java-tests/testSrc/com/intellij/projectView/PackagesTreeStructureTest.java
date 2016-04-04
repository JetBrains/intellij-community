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
package com.intellij.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.IOException;

public class PackagesTreeStructureTest extends TestSourceBasedTestCase {
  public void testPackageView() throws IOException {
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

    doTest(true, true, "-Project\n" +
                       " -Group: Group\n" +
                       "  -Module\n" +
                       "   -PsiPackage: com.package1\n" +
                       "    Class1.java\n" +
                       "    Class2.java\n" +
                       "    Class4.java\n" +
                       "    emptyClassFile.class\n" +
                       "    Form1.form\n" +
                       "    Form1.java\n" +
                       "    Form2.form\n" +
                       "   PsiPackage: empty\n" +
                       "   -PsiPackage: java\n" +
                       "    Class1.java\n" +
                       "   -PsiPackage: javax.servlet\n" +
                       "    Class1.java\n" +
                       "   -Libraries\n" +
                       "    -PsiPackage: java\n" +
                       "     +PsiPackage: awt\n" +
                       "     +PsiPackage: beans.beancontext\n" +
                       "     +PsiPackage: io\n" +
                       "     +PsiPackage: lang\n" +
                       "     +PsiPackage: net\n" +
                       "     +PsiPackage: rmi\n" +
                       "     +PsiPackage: security\n" +
                       "     +PsiPackage: sql\n" +
                       "     +PsiPackage: util\n" +
                       "    -PsiPackage: javax.swing\n" +
                       "     +PsiPackage: table\n" +
                       "     AbstractButton.class\n" +
                       "     Icon.class\n" +
                       "     JButton.class\n" +
                       "     JComponent.class\n" +
                       "     JDialog.class\n" +
                       "     JFrame.class\n" +
                       "     JLabel.class\n" +
                       "     JPanel.class\n" +
                       "     JScrollPane.class\n" +
                       "     JTable.class\n" +
                       "     SwingConstants.class\n" +
                       "     SwingUtilities.class\n" +
                       "    -PsiPackage: META-INF\n" +
                       "     MANIFEST.MF\n" +
                       "     MANIFEST.MF\n" +
                       "    -PsiPackage: org\n" +
                       "     +PsiPackage: intellij.lang.annotations\n" +
                       "     +PsiPackage: jetbrains.annotations\n" +
                       ""
      , 5);

    doTest(false, true, "-Project\n" +
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
                        "   +PsiPackage: jetbrains.annotations\n"
      , 3);

    doTest(true, false, "-Project\n" +
                    " -Group: Group\n" +
                    "  -Module\n" +
                    "   -PsiPackage: com.package1\n" +
                    "    Class1.java\n" +
                    "    Class2.java\n" +
                    "    Class4.java\n" +
                    "    emptyClassFile.class\n" +
                    "    Form1.form\n" +
                    "    Form1.java\n" +
                    "    Form2.form\n" +
                    "   PsiPackage: empty\n" +
                    "   -PsiPackage: java\n" +
                    "    Class1.java\n" +
                    "   -PsiPackage: javax.servlet\n" +
                    "    Class1.java\n", 4);

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
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);

    projectView.setShowModules(showModules, PackageViewPane.ID);

    projectView.setShowLibraryContents(showLibraryContents, PackageViewPane.ID);

    projectView.setFlattenPackages(false, PackageViewPane.ID);
    projectView.setHideEmptyPackages(true, PackageViewPane.ID);

    PackageViewPane packageViewPane = new PackageViewPane(myProject);
    packageViewPane.createComponent();
    ((AbstractProjectTreeStructure) packageViewPane.getTreeStructure()).setProviders(new ResourceBundleGrouper(myProject));
    packageViewPane.updateFromRoot(true);
    JTree tree = packageViewPane.getTree();
    TreeUtil.expand(tree, levels);
    IdeaTestUtil.assertTreeEqual(tree, expected);
    BaseProjectViewTestCase.checkContainsMethod(packageViewPane.getTreeStructure().getRootElement(), packageViewPane.getTreeStructure());
    Disposer.dispose(packageViewPane);
  }

  @Override
  protected String getTestPath() {
    return "projectView";
  }
}
