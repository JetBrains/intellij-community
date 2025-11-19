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

import com.intellij.JavaTestUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.vfs.VirtualFile;

public class ProjectViewSwitchingTest extends AbstractProjectViewTest {
  @Override
  protected String getTestPath() {
    return "projectView";
  }

  public void testRemoveAddProjectPane() {
    ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);
    assertEquals(ProjectViewPane.ID, projectView.getCurrentViewId());

    VirtualFile class1 = getContentRoot().findFileByRelativePath("src/com/package1/Class1.java");
    selectFile(class1);
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

    createTreeTest().assertStructure(expectedTreeStructure);

    projectView.removeProjectPane(projectView.getProjectViewPaneById(ProjectViewPane.ID));
    ((ProjectViewImpl)projectView).addProjectPane(new ProjectViewPane(getProject()), true);
    projectView.changeView(ProjectViewPane.ID);

    // it is important that we create a new instance of the TreeTestUtil (createTreeTest()) here,
    // because the old one has captured a reference to the old project view tree
    createTreeTest().assertStructure(expectedTreeStructure); // addProjectPane should restore state
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
