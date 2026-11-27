// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.uiDesigner.projectView.FormMergerTreeStructureProvider;
import org.jdom.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;

@TestApplication
public class ProjectTreeStateTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture();

  private String myExpectedTree;

  @Test
  public void testUpdateProjectView() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.getStructure().setProviders(new ClassesTreeStructureProvider(fixture.getProject()), new FormMergerTreeStructureProvider(fixture.getProject()));

    TreeState treeState = EdtTestUtil.runInEdtAndGet(() -> {
      AbstractProjectViewPane pane = fixture.getStructure().createPane();

      JTree tree = pane.getTree();
      PlatformTestUtil.assertTreeEqual(tree, myExpectedTree, true);

      return TreeState.createOn(tree);
    });

    doTestState(treeState);

    Element stored = new Element("Root");
    treeState.writeExternal(stored);

    TreeState readState = TreeState.createFrom(stored);
    doTestState(readState);
  }

  private void doTestState(TreeState treeState) {
    ProjectViewFixture fixture = projectViewFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      JTree tree2 = fixture.getStructure().createPane().getTree();
      treeState.applyTo(tree2);

      PlatformTestUtil.waitWhileBusy(tree2);
      PlatformTestUtil.assertTreeEqual(tree2, myExpectedTree, true);
    });
  }

  @BeforeEach
  void setUp() {
    myExpectedTree = """
      -Project
       +PsiDirectory: updateProjectView
       +External Libraries
      """;
  }
}
