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
package com.intellij.testIntegration;

import com.intellij.execution.Location;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Function;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.Time;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ShowRecentTests extends AnAction {
  private static final int TEST_LIMIT = Integer.MAX_VALUE;
  
  private static Date getSinceDate() {
    return new Date(System.currentTimeMillis() - Time.DAY);
  }
  
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }
  
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    final TestStateStorage testStorage = TestStateStorage.getInstance(project);
    final TestLocator testLocator = new TestLocator(project);
    final RecentTestRunnerImpl testRunner = new RecentTestRunnerImpl();
    
    final Map<String, TestStateStorage.Record> records = testStorage.getRecentTests(TEST_LIMIT, getSinceDate());
    RecentTestsListProvider listProvider = new RecentTestsListProvider(records);
    List<String> urls = listProvider.getUrlsToShowFromHistory();
    Map<String, Icon> icons = ContainerUtil.map2Map(urls, new Function<String, Pair<String, Icon>>() {
      @Override
      public Pair<String, Icon> fun(String url) {
        return Pair.create(url, getIconFor(url, records));
      }
    });
    SelectTestStep selectStepTest = new SelectTestStep(urls, icons, testRunner, testLocator);

    RecentTestsListPopup popup = new RecentTestsListPopup(selectStepTest, testRunner, testLocator);
    popup.showCenteredInCurrentWindow(project);
    
    ApplicationManager.getApplication().executeOnPooledThread(new DeadTestsCleaner(testStorage, urls, testLocator));
  }
  
  private static Icon getIconFor(String value, Map<String, TestStateStorage.Record> records) {
    TestStateStorage.Record record = records.get(value);
    TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(record.magnitude);
    return TestIconMapper.getIcon(magnitude);
  }
}

class RecentTestsListPopup extends ListPopupImpl {
  private final RecentTestRunner myTestRunner;
  private final TestLocator myLocator;

  public RecentTestsListPopup(ListPopupStep<String> popupStep, RecentTestRunner testRunner, TestLocator locator) {
    super(popupStep);
    myTestRunner = testRunner;
    myLocator = locator;
    
    shiftReleased();
    registerActions(this);
    
    String shift = SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift";
    setAdText("Debug with " + shift + ", navigate with F4");
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
          Location location = myLocator.getLocation(values[0].toString());
          if (location != null) {
            cancel();
            PsiNavigateUtil.navigate(location.getPsiElement());
          }
        }
      }
    });
  }

  private void shiftPressed() {
    setCaption("Debug Recent Tests");
    myTestRunner.setMode(RecentTestRunner.Mode.DEBUG);
  }

  private void shiftReleased() {
    setCaption("Run Recent Tests");
    myTestRunner.setMode(RecentTestRunner.Mode.RUN);
  }
}


