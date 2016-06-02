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
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.tests.gui.fixtures.newProjectWizard.NewProjectWizardFixture;
import com.intellij.tests.gui.framework.GuiTestCase;
import com.intellij.tests.gui.framework.GuiTests;
import com.intellij.tests.gui.framework.IdeGuiTest;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.io.IOException;


/**
 * Created by karashevich on 27/05/16.
 */
public class NewProjectTest extends GuiTestCase {


  //Should run with main_idea_tests module classpath
  @Test @IdeGuiTest
  public void testNewProject() throws IOException, InterruptedException {
    String myName = "TestProject";
    String myDomain = "com.test";
    String myPkg = "com.android.test.app";

    //PREREQUISITES
    String sdkPath = "/Library/Java/JavaVirtualMachines/jdk1.8.0_71.jdk/Contents/Home";
    File javaSdkPath = new File(sdkPath);
    String sdkType = "JDK";

    findWelcomeFrame().createNewProject();
    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    //check Project SDK is not Empty
    if (newProjectWizard.isJdkEmpty()) {
      JButton newButton = GuiTests.findButton(newProjectWizard,
                                              GuiTests.adduction(ApplicationBundle.message("button.new")),
                                              myRobot);
      myRobot.click(newButton);
      GuiTests.clickPopupMenuItem(GuiTests.adduction(ProjectBundle.message("sdk.java.name")), newButton, myRobot);
      newProjectWizard.selectSdkPath(javaSdkPath, sdkType);
    }

    newProjectWizard.
      selectProjectType("Java").
      selectFramework("Struts");

    //newProjectWizard.

    Thread.sleep(10000);

    //newProjectWizard.clickNext()
    //
    //newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, myMinSdk);
    //newProjectWizard.clickNext();

    // Skip "Add Activity" step
    newProjectWizard.clickNext();


    //newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);ё
    //newProjectWizard.clickFinish();

  }

  @NotNull
  NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }

  private class NewProjectDescriptor {
    private String myActivity = "MainActivity";
    private String myPkg = "com.android.test.app";
    private String myMinSdk = "19";
    private String myName = "TestProject";
    private String myDomain = "com.android";
    private boolean myWaitForSync = true;


    private NewProjectDescriptor(@NotNull String name) {
      withName(name);
    }

    /**
     * Set a custom package to use in the new project
     */
    NewProjectDescriptor withPackageName(@NotNull String pkg) {
      myPkg = pkg;
      return this;
    }

    /**
     * Set a new project name to use for the new project
     */
    NewProjectDescriptor withName(@NotNull String name) {
      myName = name;
      return this;
    }

    /**
     * Set a custom activity name to use in the new project
     */
    NewProjectDescriptor withActivity(@NotNull String activity) {
      myActivity = activity;
      return this;
    }

    /**
     * Set a custom minimum SDK version to use in the new project
     */
    NewProjectDescriptor withMinSdk(@NotNull String minSdk) {
      myMinSdk = minSdk;
      return this;
    }

    /**
     * Set a custom company domain to enter in the new project wizard
     */
    NewProjectDescriptor withCompanyDomain(@NotNull String domain) {
      myDomain = domain;
      return this;
    }

    /**
     * Picks brief names in order to make the test execute faster (less slow typing in name text fields)
     */
    NewProjectDescriptor withBriefNames() {
      withActivity("A").withCompanyDomain("C").withName("P").withPackageName("a.b");
      return this;
    }

    /** Turns off the automatic wait-for-sync that normally happens on {@link #create} */
    NewProjectDescriptor withoutSync() {
      myWaitForSync = false;
      return this;
    }

    /**
     * Creates a project fixture for this description
     */
    void create() {
      findWelcomeFrame().createNewProject();

      NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

      newProjectWizard.clickNext();

      newProjectWizard.clickNext();

      // Skip "Add Activity" step
      newProjectWizard.clickNext();

      newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);
      newProjectWizard.clickFinish();

    }
  }


}
