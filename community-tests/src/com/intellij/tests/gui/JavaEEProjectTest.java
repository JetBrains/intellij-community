/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tests.gui;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.tests.gui.fixtures.ProjectViewFixture;
import com.intellij.tests.gui.fixtures.ToolWindowFixture;
import com.intellij.tests.gui.fixtures.newProjectWizard.NewProjectWizardFixture;
import com.intellij.tests.gui.framework.*;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.intellij.tests.gui.framework.GuiTests.getSystemJdk;


/**
 * Created by karashevich on 27/05/16.
 */
@BelongsToTestGroups({TestGroup.PROJECT})
public class JavaEEProjectTest extends GuiTestCase {


  //Should run with main_idea_tests module classpath
  @Test @IdeGuiTest
  public void testJavaEEProject() throws IOException, InterruptedException {

    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
    Date date = new Date();
    String projectName = "smoke-test-project-" + dateFormat.format(date);


    findWelcomeFrame().createNewProject();
    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    //check Project SDK is not Empty
    setupJdk(newProjectWizard);

    //select project type and framework
    newProjectWizard.
      selectProjectType("Java").
      selectFramework("JavaEE Persistence");

    newProjectWizard.clickNext();

    newProjectWizard.setProjectName(projectName);
    final File locationInFileSystem = newProjectWizard.getLocationInFileSystem();
    newProjectWizard.clickFinish();

    myProjectFrame = findIdeFrame(projectName, locationInFileSystem);

    final ProjectViewFixture projectView = myProjectFrame.getProjectView();
    final ProjectViewFixture.PaneFixture paneFixture = projectView.selectProjectPane();

    paneFixture.selectByPath(projectName, "src", "META-INF", "persistence.xml");
    ToolWindowFixture.showToolwindowStripes(myRobot);

    //final JToggleButtonFixture persistence = (new JToggleButtonFinder("Persistence")).withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(myRobot);
    //persistence.click();

    //ToolWindowFixture.clickToolwindowButton("Persistence", myRobot);
    //ToolWindowFixture.clickToolwindowButton("Java Enterprise", myRobot);
  }

  private void setupJdk(NewProjectWizardFixture newProjectWizard) {
    if (newProjectWizard.isJdkEmpty()) {
      JButton newButton = GuiTests.findButton(newProjectWizard,
                                              GuiTests.adduction(ApplicationBundle.message("button.new")),
                                              myRobot);
      myRobot.click(newButton);
      File javaSdkPath = new File(getSystemJdk());
      String sdkType = GuiTests.adduction(ProjectBundle.message("sdk.java.name"));
      GuiTests.clickPopupMenuItem(sdkType, newButton, myRobot);
      newProjectWizard.selectSdkPath(javaSdkPath, sdkType);
    }
  }

}
