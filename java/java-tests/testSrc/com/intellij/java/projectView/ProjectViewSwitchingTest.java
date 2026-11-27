// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.projectView.ProjectViewPaneFixtureKt.projectViewPaneFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ProjectViewSwitchingTest {
  private final TestFixture<ProjectViewPaneFixture> paneFixture = projectViewPaneFixture("projectView", false);

  @Test
  public void testRemoveAddProjectPane() {
    ProjectViewPaneFixture fixture = paneFixture.get();
    ProjectView projectView = ProjectView.getInstance(fixture.getProject());
    EdtTestUtil.runInEdtAndWait(() -> projectView.changeView(ProjectViewPane.ID));
    assertEquals(ProjectViewPane.ID, projectView.getCurrentViewId());

    VirtualFile class1 = fixture.getContentRoot().findFileByRelativePath("src/com/package1/Class1.java");
    fixture.selectFile(class1);
    String expectedTreeStructure = """
       -PsiDirectory: removeAddProjectPane
        -PsiDirectory: src
         -PsiDirectory: com
          -PsiDirectory: package1
           Class1
           +Class2.java
           Class4.java
           Form1
       +External Libraries
      """;

    fixture.assertStructure(fixture.createTreeTest(), expectedTreeStructure);

    EdtTestUtil.runInEdtAndWait(() -> {
      projectView.removeProjectPane(projectView.getProjectViewPaneById(ProjectViewPane.ID));
      ((ProjectViewImpl)projectView).addProjectPane(new ProjectViewPane(fixture.getProject()), true);
      projectView.changeView(ProjectViewPane.ID);
    });

    // it is important that we create a new instance of the TreeTestUtil (createTreeTest()) here,
    // because the old one has captured a reference to the old project view tree
    fixture.assertStructure(fixture.createTreeTest(), expectedTreeStructure); // addProjectPane should restore state
  }
}
