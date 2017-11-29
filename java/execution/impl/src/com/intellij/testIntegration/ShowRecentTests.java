/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Time;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ShowRecentTests extends AnAction {
  private static final int TEST_LIMIT = Integer.MAX_VALUE;
  private static final String ID = "show.recent.tests.action";

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

    UsageTrigger.trigger(ID);

    final TestStateStorage testStorage = TestStateStorage.getInstance(project);
    final TestLocator testLocator = new TestLocator(project);
    final RecentTestRunnerImpl testRunner = new RecentTestRunnerImpl(testLocator);
    
    final Map<String, TestStateStorage.Record> records = testStorage.getRecentTests(TEST_LIMIT, getSinceDate());

    RunConfigurationByRecordProvider configurationProvider = new RunConfigurationByRecordProvider(project);
    RecentTestsListProvider listProvider = new RecentTestsListProvider(configurationProvider, records);
    
    List<RecentTestsPopupEntry> entries = listProvider.getTestsToShow();
    
    SelectTestStep selectStepTest = new SelectTestStep("Debug Recent Tests", entries, testRunner);

    RecentTestsListPopup popup = new RecentTestsListPopup(selectStepTest, testRunner, testLocator);
    popup.showCenteredInCurrentWindow(project);

    cleanDeadTests(entries, testLocator, testStorage);
  }

  private static void cleanDeadTests(List<RecentTestsPopupEntry> entries, TestLocator testLocator, TestStateStorage testStorage) {
    UrlsCollector collector = new UrlsCollector();
    entries.forEach((e) -> e.accept(collector));
    ApplicationManager.getApplication().executeOnPooledThread(new DeadTestsCleaner(testStorage, collector.getUrls(), testLocator));
  }
}



