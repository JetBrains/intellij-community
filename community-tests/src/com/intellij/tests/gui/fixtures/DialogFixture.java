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
package com.intellij.tests.gui.fixtures;

import com.intellij.openapi.project.ProjectBundle;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by jetbrains on 22/08/16.
 */
public class DialogFixture implements ContainerFixture<JDialog> {

  private JDialog myDialog;
  private Robot myRobot;

  public DialogFixture(@NotNull Robot robot, JDialog selectSdkDialog) {
    myRobot = robot;
    myDialog = selectSdkDialog;
  }

  @NotNull
  public static DialogFixture find(@NotNull Robot robot, String title) {
    JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return title.equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new DialogFixture(robot, dialog);
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
