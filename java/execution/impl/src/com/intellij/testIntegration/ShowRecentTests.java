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

import com.intellij.execution.Location;
import com.intellij.execution.TestStateStorage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.Time;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Date;
import java.util.Map;

public class ShowRecentTests extends AnAction {
  private static final int TEST_LIMIT = Integer.MAX_VALUE;
  
  private static Date getSinceDate() {
    return new Date(System.currentTimeMillis() - Time.DAY);
  }
  
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;

    Map<String, TestStateStorage.Record> records = TestStateStorage.getInstance(project).getRecentTests(TEST_LIMIT, getSinceDate());
    RecentTestRunner testRunner = new RecentTestRunnerImpl(project);
    
    SelectTestStep selectStepTest = new SelectTestStep(records, testRunner);
    RecentTestsListPopup popup = new RecentTestsListPopup(selectStepTest, testRunner);
    popup.showCenteredInCurrentWindow(project);
  }
}

class RecentTestsListPopup extends ListPopupImpl {
  private final RecentTestRunner myTestRunner;

  public RecentTestsListPopup(ListPopupStep<String> popupStep, RecentTestRunner testRunner) {
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
    popup.registerAction("navigate", KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object[] values = getSelectedValues();
        if (values.length == 1) {
          Location location = myTestRunner.getLocation(values[0].toString());
          if (location != null) {
            cancel();
            PsiNavigateUtil.navigate(location.getPsiElement());
          }
        }
      }
    });
  }

  private void shiftPressed() {
    setCaption("Run Recent Tests");
    myTestRunner.setMode(RecentTestRunner.Mode.RUN);
  }

  private void shiftReleased() {
    setCaption("Debug Recent Tests");
    myTestRunner.setMode(RecentTestRunner.Mode.DEBUG);
  }
}


