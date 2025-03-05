// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.testframework.actions.TestFrameworkActions;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.events.TestDurationStrategy;
import com.intellij.execution.testframework.sm.runner.ui.*;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

@ApiStatus.Experimental
@ApiStatus.Internal
public class JavaSMTRunnerTestTreeView extends SMTRunnerTestTreeView implements SMTRunnerTestTreeViewProvider.CustomizedDurationProvider {
  private static final Key<Long> FIRST_CHILD_START = Key.create("JavaSMTRunnerTestTreeView.FIRST_CHILD_START");

  private final @NotNull TestConsoleProperties myTestConsoleProperties;

  public JavaSMTRunnerTestTreeView(@NotNull TestConsoleProperties testConsoleProperties) {
    myTestConsoleProperties = testConsoleProperties;
  }

  @Override
  public void setTestResultsViewer(TestResultsViewer resultsViewer) {
    super.setTestResultsViewer(resultsViewer);
    if (resultsViewer instanceof SMTestRunnerResultsForm testFrameworkRunningModel) {
      TestFrameworkActions.addPropertyListener(JavaAwareTestConsoleProperties.USE_WALL_TIME,
                                               new TestFrameworkPropertyListener<>() {
                                                 @Override
                                                 public void onChanged(Boolean ignore) {
                                                   testFrameworkRunningModel.redrawStatusLabel();
                                                 }
                                               }
        , testFrameworkRunningModel, true);
    }
  }

  @Override
  protected TreeCellRenderer getRenderer(TestConsoleProperties properties) {
    return new TestTreeRenderer(properties) {

      @Nls
      @Override
      public @Nullable String getDurationText(@NotNull SMTestProxy testProxy,
                                              @NotNull TestConsoleProperties consoleProperties) {
        if (testProxy.getDurationStrategy() != TestDurationStrategy.AUTOMATIC) {
          return testProxy.getDurationString(consoleProperties);
        }
        if (testProxy.isInProgress() && !testProxy.isSubjectToHide(consoleProperties)) {
          Long startedAt;
          if (testProxy.isSuite() && !JavaAwareTestConsoleProperties.USE_WALL_TIME.value(consoleProperties)) {
            startedAt = getFirstChildStartedAt(testProxy);
          }
          else {
            startedAt = testProxy.getStartTimeMillis();
          }
          if (startedAt == null || startedAt == 0) {
            return null;
          }
          long durationMillis = System.currentTimeMillis() - startedAt;
          long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis);
          if (durationSeconds == 0) return null;
          durationMillis = TimeUnit.SECONDS.toMillis(durationSeconds);
          return NlsMessages.formatDurationApproximateNarrow(durationMillis);
        }
        if (testProxy.isSuite() &&
            testProxy.isFinal() &&
            !testProxy.isSubjectToHide(consoleProperties) &&
            JavaAwareTestConsoleProperties.USE_WALL_TIME.value(consoleProperties)) {
          Long startTime = testProxy.getStartTimeMillis();
          Long endTime = testProxy.getEndTimeMillis();
          if (startTime != null && endTime != null && startTime < endTime) {
            return NlsMessages.formatDurationApproximateNarrow(endTime - startTime);
          }
        }
        return testProxy.getDurationString(consoleProperties);
      }

      private static @Nullable Long getFirstChildStartedAt(@NotNull SMTestProxy testProxy) {
        Long data = testProxy.getUserData(FIRST_CHILD_START);
        if (data != null) {
          return data;
        }
        Long minTime = null;
        for (SMTestProxy child : testProxy.getChildren()) {
          Long time;
          if (child.isLeaf()) {
            time = child.getStartTimeMillis();
          }
          else {
            time = getFirstChildStartedAt(child);
          }
          if (time != null && (minTime == null || time < minTime)) {
            minTime = time;
          }
        }
        if (minTime != null) {
          testProxy.putUserData(FIRST_CHILD_START, minTime);
        }
        return minTime;
      }
    }

      ;
  }

  @Nls
  @Override
  public String getToolTipText(MouseEvent event) {
    String text = super.getToolTipText(event);
    if (text != null) {
      return text;
    }
    if (event == null) {
      return null;
    }
    Point p = event.getPoint();
    TreePath location = getClosestPathForLocation(p.x, p.y);
    if (location == null) {
      return null;
    }
    SMTestProxy test = getSelectedTest(location);

    if (test == null || test.isLeaf() || test.getDurationStrategy() != TestDurationStrategy.AUTOMATIC ||
        test.getEndTimeMillis() == null || test.getStartTimeMillis() == null ||
        test.getEndTimeMillis() <= test.getStartTimeMillis()) {
      return null;
    }

    if (getWidth() / 2 < Math.abs(p.x)) {
      long overallDuration = test.getEndTimeMillis() - test.getStartTimeMillis();
      Long sumDuration = test.getDuration();
      String durationText = "";
      durationText += JavaBundle.message("java.test.overall.time", NlsMessages.formatDurationApproximateNarrow(overallDuration));
      if (sumDuration != null) {
        durationText += "<br>";
        durationText += JavaBundle.message("java.test.sum.time", NlsMessages.formatDurationApproximateNarrow(sumDuration));
      }
      return "<html>" + durationText + "</html>";
    }
    return null;
  }

  @Override
  public Long getCustomizedDuration(@NotNull SMTestProxy proxy) {
    if (!proxy.isSuite() || proxy.getDurationStrategy() != TestDurationStrategy.AUTOMATIC ||
        !JavaAwareTestConsoleProperties.USE_WALL_TIME.value(myTestConsoleProperties)) {
      return proxy.getDuration();
    }
    Long startTime = proxy.getStartTimeMillis();
    Long endTime = proxy.getEndTimeMillis();
    if (endTime == null && proxy.isInProgress()) {
      endTime = System.currentTimeMillis();
    }
    if (startTime == null || endTime == null || startTime >= endTime) {
      return null;
    }
    return endTime - startTime;
  }
}
