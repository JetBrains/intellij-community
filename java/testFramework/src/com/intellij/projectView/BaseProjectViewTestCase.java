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
package com.intellij.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class BaseProjectViewTestCase extends TestSourceBasedTestCase {
  protected TestProjectTreeStructure myStructure;

  protected Queryable.PrintInfo myPrintInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStructure = new TestProjectTreeStructure(myProject, myTestRootDisposable);
  }

  @Override
  protected void tearDown() throws Exception {
    myStructure = null;
    super.tearDown();
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, @NonNls String expected) {
    assertStructureEqual(packageDirectory, expected, 17, myStructure);
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, @NonNls String expected, int maxRowCount) {
    assertStructureEqual(packageDirectory, expected, maxRowCount, myStructure);
  }

  protected void useStandardProviders() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject));
  }

  protected AbstractProjectTreeStructure getProjectTreeStructure() {
    return myStructure;
  }

  private void assertStructureEqual(PsiDirectory root, String expected, int maxRowCount, AbstractTreeStructure structure) {
    assertNotNull(root);
    PsiDirectoryNode rootNode = new PsiDirectoryNode(myProject, root, (ViewSettings)structure);
    ProjectViewTestUtil.assertStructureEqual(myStructure, expected, maxRowCount, PlatformTestUtil.createComparator(myPrintInfo), rootNode, myPrintInfo);
  }

  protected static void assertListsEqual(ListModel model, String expected) {
    assertEquals(expected, PlatformTestUtil.print(model));
  }

  public static void checkContainsMethod(final Object rootElement, final AbstractTreeStructure structure) {
    ProjectViewTestUtil.checkContainsMethod(rootElement, structure, new Function<AbstractTreeNode, VirtualFile[]>() {
      @Override
      public VirtualFile[] fun(AbstractTreeNode kid) {
        if (kid instanceof PackageElementNode) {
          return ((PackageElementNode)kid).getVirtualFiles();
        }
        return null;
      }
    });
  }

  @Override
  protected String getTestPath() {
    return "projectView";
  }

  protected static String getPackageRelativePath() {
    return "com/package1";
  }

  protected PsiDirectory getPackageDirectory() {
    return getPackageDirectory(getPackageRelativePath());
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath(getClass());
  }

}
