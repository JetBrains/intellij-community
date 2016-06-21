/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests.gui.fixtures.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.tests.gui.fixtures.FrameworksTreeFixture;
import com.intellij.tests.gui.fixtures.SelectSdkDialogFixture;
import com.intellij.tests.gui.framework.GuiTests;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBList;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.intellij.tests.gui.framework.GuiTests.LONG_TIMEOUT;
import static com.intellij.tests.gui.framework.GuiTests.getSystemJdk;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.timing.Pause.pause;

public class NewProjectWizardFixture extends AbstractWizardFixture<NewProjectWizardFixture> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {

    final DialogFixture newProjectDialog = findDialog(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return IdeBundle.message("title.new.project").equals(dialog.getTitle()) && dialog.isShowing();
      }
    }).withTimeout(LONG_TIMEOUT.duration()).using(robot);


    return new NewProjectWizardFixture(robot, (JDialog)newProjectDialog.target());
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public boolean isJdkEmpty() {
    final JdkComboBox jdkComboBox = robot().finder().findByType(JdkComboBox.class);
    return (jdkComboBox.getSelectedJdk() == null);
  }

  @NotNull
  public NewProjectWizardFixture selectProjectType(String projectTypeName) {
    JListFixture projectTypeList = new JListFixture(robot(), robot().finder().findByType(this.target(), JBList.class, true));
    projectTypeList.clickItem(projectTypeName);
    return this;
  }

  @NotNull
  public NewProjectWizardFixture selectFramework(String frameworkName) {
    final FrameworksTreeFixture frameworksTreeFixture = FrameworksTreeFixture.find(robot());
    frameworksTreeFixture.selectFramework(frameworkName);
    return this;
  }

  public NewProjectWizardFixture selectSdkPath(@NotNull File sdkPath, String sdkType) {

    final SelectSdkDialogFixture sdkDialogFixture = SelectSdkDialogFixture.find(robot(), sdkType);

    sdkDialogFixture.selectPathToSdk(sdkPath).clickOk();
    pause(new Condition("Waiting for the returning of focus to dialog: " + target().getTitle()) {
      @Override
      public boolean test() {
        return target().isFocused();
      }
    }, GuiTests.SHORT_TIMEOUT);
    return this;
  }

  @NotNull
  public NewProjectWizardFixture setProjectName(@NotNull String projectName) {
    String labelText = GuiTests.adduction(IdeBundle.message("label.project.name"));

    pause(new Condition("Waiting for the sliding to project name settings") {
      @Override
      public boolean test() {
        final Component label = robot().finder().findByLabel(labelText);
        return label != null;
      }
    }, GuiTests.SHORT_TIMEOUT);
    final JTextComponentFixture textField = findTextField(labelText);
    robot().click(textField.target());
    textField.setText(projectName);
    return this;
  }

  @NotNull
  public File getLocationInFileSystem() {
    String labelText = GuiTests.adduction(IdeBundle.message("label.project.files.location"));
    //noinspection ConstantConditions
    final JTextComponentFixture locationTextField = findTextField(labelText);
    return execute(new GuiQuery<File>() {
      @Override
      protected File executeInEDT() throws Throwable {
        String location = locationTextField.text();
        assertThat(location).isNotNull().isNotEmpty();
        return new File(location);
      }
    });
  }

  public NewProjectWizardFixture setupJdk() {
    if (this.isJdkEmpty()) {
      JButton newButton = GuiTests.findButton(this,
                                              GuiTests.adduction(ApplicationBundle.message("button.new")),
                                              robot());
      robot().click(newButton);
      File javaSdkPath = new File(getSystemJdk());
      String sdkType = GuiTests.adduction(ProjectBundle.message("sdk.java.name"));
      GuiTests.clickPopupMenuItem(sdkType, newButton, robot());
      this.selectSdkPath(javaSdkPath, sdkType);
    }
    return this;
  }


  @NotNull
  public ConfigureAndroidProjectStepFixture getConfigureAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Configure your new project");
    return new ConfigureAndroidProjectStepFixture(robot(), rootPane);
  }

  @NotNull
  public ConfigureFormFactorStepFixture getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Select the form factors your app will run on");
    return new ConfigureFormFactorStepFixture(robot(), rootPane);
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture getChooseOptionsForNewFileStep() {
    JRootPane rootPane = findStepWithTitle("Customize the Activity");
    return new ChooseOptionsForNewFileStepFixture(robot(), rootPane);
  }

}
