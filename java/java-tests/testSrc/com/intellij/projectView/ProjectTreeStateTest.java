// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.uiDesigner.projectView.FormMergerTreeStructureProvider;
import org.jdom.Element;

import javax.swing.*;

public class ProjectTreeStateTest extends BaseProjectViewTestCase {
  private String myExpectedTree;

  public void testUpdateProjectView() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    AbstractProjectViewPSIPane pane = myStructure.createPane();

    JTree tree = pane.getTree();
    tree.expandRow(1);
    tree.expandRow(5);
    tree.expandRow(6);
    tree.expandRow(9);
    tree.expandRow(11);
    PlatformTestUtil.assertTreeEqual(pane.getTree(), myExpectedTree, true);

    TreeState treeState = TreeState.createOn(tree);

    doTestState(treeState);

    Element stored = new Element("Root");
    treeState.writeExternal(stored);

    TreeState readState = TreeState.createFrom(stored);
    doTestState(readState);
  }

  private void doTestState(TreeState treeState) {
    JTree tree2 = myStructure.createPane().getTree();
    treeState.applyTo(tree2);

    PlatformTestUtil.waitWhileBusy(tree2);
    PlatformTestUtil.assertTreeEqual(tree2, myExpectedTree, true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myExpectedTree = "-Project\n" +
                     " +PsiDirectory: updateProjectView\n" +
                     getRootFiles() +
                     " +External Libraries\n";
  }
}
