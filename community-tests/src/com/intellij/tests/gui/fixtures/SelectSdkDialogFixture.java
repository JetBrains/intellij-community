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
package com.intellij.tests.gui.fixtures;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.projectWizard.ProjectJdkStep;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.tests.gui.fixtures.newProjectWizard.NewProjectWizardFixture;
import com.intellij.tests.gui.framework.GuiTests;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.intellij.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;

public class SelectSdkDialogFixture implements ContainerFixture<JDialog>{

  private JDialog myDialog;
  private Robot myRobot;

  public SelectSdkDialogFixture(@NotNull Robot robot, JDialog selectSdkDialog) {
    myRobot = robot;
    myDialog = selectSdkDialog;
  }

  @NotNull
  public static SelectSdkDialogFixture find(@NotNull Robot robot, String sdkType) {
    JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return ProjectBundle.message("sdk.configure.home.title", sdkType).equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new SelectSdkDialogFixture(robot, dialog);
  }

  public SelectSdkDialogFixture selectPathToSdk(@NotNull File pathToSdk) {
    final JTextField textField = myRobot.finder().findByType(JTextField.class);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        textField.setText(pathToSdk.getPath());
      }
    });


    final Tree tree = myRobot.finder().findByType(myDialog, Tree.class);
    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
    pause(new Condition("Wait until path is updated") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return (textField.getText().equals(pathToSdk.getPath()) && !builder.getUi().getUpdater().hasNodesToUpdate()) ;
          }
        });
      }
    }, SHORT_TIMEOUT);
    return this;
  }

  public void clickOk(){
    pause(new Condition("Waiting when ok button at SDK select dialog will be ready for a click") {
      @Override
      public boolean test() {
        JButton button = GuiTests.findButton(SelectSdkDialogFixture.this, "OK", myRobot);
        return button.isEnabled();
      }
    }, GuiTests.SHORT_TIMEOUT);

    GuiTests.findAndClickOkButton(this);
  }

  @Override
  public JDialog target() {
    return myDialog;
  }

  @Override
  public Robot robot() {
    return myRobot;
  }
}


//extends IdeaDialogFixture<SelectSdkDialog> {
//@NotNull
//public static SelectSdkDialogFixture find(@NotNull Robot robot) {
//  return new SelectSdkDialogFixture(robot, find(robot, SelectSdkDialog.class));
//}
//
//private SelectSdkDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<SelectSdkDialog> dialogAndWrapper) {
//  super(robot, dialogAndWrapper);
//}
//
//@NotNull
//public SelectSdkDialogFixture setJdkPath(@NotNull final File path) {
//  final JLabel label = robot().finder().find(target(), JLabelMatcher.withText("Select Java JDK:").andShowing());
//  execute(new GuiTask() {
//    @Override
//    protected void executeInEDT() throws Throwable {
//      Component textField = label.getLabelFor();
//      assertThat(textField).isInstanceOf(JTextField.class);
//      ((JTextField)textField).setText(path.getPath());
//    }
//  });
//  return this;
//}
//
//@NotNull
//public SelectSdkDialogFixture clickOk() {
//  findAndClickOkButton(this);
//  return this;
//}
