// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import org.jetbrains.annotations.NotNull;

public interface RecentTestRunner {
  enum Mode {
    RUN,
    DEBUG
  }

  void setMode(Mode mode);

  void run(RecentTestsPopupEntry entry);
}

class RecentTestRunnerImpl implements RecentTestRunner {
  // false positives IDEA-356383
  @SuppressWarnings("UnresolvedPluginConfigReference")
  private static final AnAction RUN = ActionManager.getInstance().getAction("RunClass");
  @SuppressWarnings("UnresolvedPluginConfigReference")
  private static final AnAction DEBUG = ActionManager.getInstance().getAction("DebugClass");

  private final TestLocator myTestLocator;

  protected AnAction myCurrentAction = RUN;

  @Override
  public void setMode(Mode mode) {
    myCurrentAction = switch (mode) {
      case RUN -> RUN;
      case DEBUG -> DEBUG;
    };
  }

  RecentTestRunnerImpl(TestLocator testLocator) {
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
    Location<?> location = myTestLocator.getLocation(url);
    if (location == null) {
      return;
    }
    DataContext context = SimpleDataContext.getSimpleContext(Location.DATA_KEY, location);
    myCurrentAction.actionPerformed(AnActionEvent.createFromAnAction(myCurrentAction, null, "", context));
  }
}
