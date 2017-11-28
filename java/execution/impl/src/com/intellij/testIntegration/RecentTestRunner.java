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

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RecentTestRunner {
  enum Mode { 
    RUN, 
    DEBUG 
  }
  
  void setMode(Mode mode);
  
  void run(RecentTestsPopupEntry entry);
}

class RecentTestRunnerImpl implements RecentTestRunner {
  private static AnAction RUN = ActionManager.getInstance().getAction("RunClass");
  private static AnAction DEBUG = ActionManager.getInstance().getAction("DebugClass");

  private final TestLocator myTestLocator;

  protected AnAction myCurrentAction = RUN;
  
  public void setMode(Mode mode) {
    switch (mode) {
      case RUN:
        myCurrentAction = RUN;
        break;
      case DEBUG:
        myCurrentAction = DEBUG;
        break;
    }
  }

  public RecentTestRunnerImpl(TestLocator testLocator) {
    myTestLocator = testLocator;
  }

  @Override
  public void run(RecentTestsPopupEntry entry) {
    entry.accept(new TestEntryVisitor() {
      @Override
      public void visitTest(@NotNull SingleTestEntry test) {
        run(test.getUrl());
      }

      @Override
      public void visitSuite(@NotNull SuiteEntry suite) {
        run(suite.getSuiteUrl());
      }

      @Override
      public void visitRunConfiguration(@NotNull RunConfigurationEntry configuration) {
        run(configuration.getRunSettings());
      }
    });
  }

  private void run(RunnerAndConfigurationSettings configuration) {
    Executor executor = myCurrentAction == RUN ? DefaultRunExecutor.getRunExecutorInstance() 
                                               : DefaultDebugExecutor.getDebugExecutorInstance();
    
    ProgramRunnerUtil.executeConfiguration(configuration, executor);
  }

  private void run(@NotNull String url) {
    Location location = myTestLocator.getLocation(url);
    if (location == null) {
      return;
    }

    DataContext data = new DataContext() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (Location.DATA_KEY.is(dataId)) {
          return location;
        }
        return null;
      }
    };
    
    myCurrentAction.actionPerformed(AnActionEvent.createFromAnAction(myCurrentAction, null, "", data));
  }
}
