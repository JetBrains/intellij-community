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
package com.intellij.testIntegration;

import com.intellij.execution.TestStateStorage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Time;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.Map;

public class ShowRecentTests extends AnAction {
  private static final int TEST_LIMIT = 20;
  
  private static Date getSinceDate() {
    return new Date(System.currentTimeMillis() - 2 * Time.HOUR);
  }
  
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;

    Map<String, TestStateStorage.Record> records = TestStateStorage.getInstance(project).getRecentTests(TEST_LIMIT, getSinceDate());
    LocationTestRunner testRunner = new LocationTestRunnerImpl();
    
    SelectTestStep selectStepTest = new SelectTestStep(project, records, testRunner);
    RecentTestsListPopup popup = new RecentTestsListPopup(selectStepTest, testRunner);
    popup.showCenteredInCurrentWindow(project);
  }
}

class RecentTestsListPopup extends ListPopupImpl {
  private final LocationTestRunner myTestRunner;

  public RecentTestsListPopup(ListPopupStep<String> popupStep, LocationTestRunner testRunner) {
    super(popupStep);
    myTestRunner = testRunner;
    shiftReleased();
    registerActions(this);
  }

  private void registerActions(ListPopupImpl popup) {
    popup.registerAction("alternate", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftPressed();
      }
    });
    popup.registerAction("restoreDefault", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        shiftReleased();
      }
    });
    popup.registerAction("invokeAction", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleSelect(true);
      }
    });
  }

  private void shiftPressed() {
    setCaption("Run Recent Tests");
    myTestRunner.setMode(LocationTestRunner.Mode.RUN);
  }

  private void shiftReleased() {
    setCaption("Debug Recent Tests");
    myTestRunner.setMode(LocationTestRunner.Mode.DEBUG);
  }
}


