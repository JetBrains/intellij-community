// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.testframework;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaSMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeViewProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaSMTRunnerWallTimeTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  private static final String TEST_FRAMEWORK_NAME = "JavaSMTRunnerWallTimeTest";

  private SMTRunnerConsoleView myConsole;
  private SMRootTestProxy myRootNode;
  private ProcessHandler myProcessHandler;

  @BeforeEach
  void setUp() {
    EdtTestUtil.runInEdtAndWait(() -> createConsole(true));
  }

  private void createConsole(boolean idBasedTestTree) {
    SMTRunnerConsoleProperties properties = new WallTimeAwareConsoleProperties(projectFixture.get().getProject());
    properties.setIdBasedTestTree(idBasedTestTree);
    JavaAwareTestConsoleProperties.USE_WALL_TIME.primSet(properties, true);
    myConsole = (SMTRunnerConsoleView)SMTestRunnerConnectionUtil.createConsole(TEST_FRAMEWORK_NAME, properties);
    myRootNode = myConsole.getResultsViewer().getTestsRootNode();
    myProcessHandler = new NopProcessHandler();
    myConsole.attachToProcess(myProcessHandler);
    myProcessHandler.startNotify();
  }

  @AfterEach
  void tearDown() {
    try {
      if (myProcessHandler != null) {
        myProcessHandler.destroyProcess();
      }
    }
    finally {
      if (myConsole != null) {
        EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myConsole));
      }
    }
  }

  @Test
  public void testSuiteFinishedWithWallTimeDuration() {
    notifyStdoutLineAvailable("##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    notifyStdoutLineAvailable("##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='suite']");

    notifyStdoutLineAvailable("##teamcity[testStarted nodeId='2' parentNodeId='1' name='test']");
    notifyStdoutLineAvailable("##teamcity[testFinished nodeId='2' duration='100']");

    notifyStdoutLineAvailable("##teamcity[testSuiteFinished nodeId='1' duration='500']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");

    SMTestProxy suite = myRootNode.getChildren().getFirst();
    assertNotNull(suite);
    Long suiteDuration = suite.getCustomizedDuration(myConsole.getProperties());
    assertNotNull(suiteDuration);
    assertEquals(500L, suiteDuration.longValue());
  }

  @Test
  public void testNameBasedSuiteFinishedWithWallTimeDuration() {
    myProcessHandler.destroyProcess();
    EdtTestUtil.runInEdtAndWait(() -> {
      Disposer.dispose(myConsole);
      createConsole(false);
    });

    notifyStdoutLineAvailable("##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    notifyStdoutLineAvailable("##teamcity[testSuiteStarted name='suite']");

    notifyStdoutLineAvailable("##teamcity[testStarted name='test']");
    notifyStdoutLineAvailable("##teamcity[testFinished name='test' duration='100']");

    notifyStdoutLineAvailable("##teamcity[testSuiteFinished name='suite' duration='501']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");

    SMTestProxy suite = myRootNode.getChildren().getFirst();
    assertNotNull(suite);
    Long suiteDuration = suite.getCustomizedDuration(myConsole.getProperties());
    assertNotNull(suiteDuration);
    assertEquals(501L, suiteDuration.longValue());
  }

  @Test
  public void testRootSuiteWithExplicitDuration_reportsWallTime() {
    notifyStdoutLineAvailable("##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    notifyStdoutLineAvailable("##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='suite']");
    notifyStdoutLineAvailable("##teamcity[testStarted nodeId='2' parentNodeId='1' name='test']");

    SMTestProxy suite = myRootNode.getChildren().getFirst();
    assertNull(suite.getDuration()); // children not finished yet — sum-of-children is unknown

    notifyStdoutLineAvailable("##teamcity[testFinished nodeId='2' duration='100']");
    notifyStdoutLineAvailable("##teamcity[testSuiteFinished nodeId='1' duration='200']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");

    assertEquals(200L, suite.getCustomizedDuration(myConsole.getProperties()).longValue());
  }

  private void notifyStdoutLineAvailable(@NotNull String line) {
    myProcessHandler.notifyTextAvailable(line + "\n", ProcessOutputTypes.STDOUT);
  }

  private static final class WallTimeAwareConsoleProperties extends SMTRunnerConsoleProperties
    implements SMTRunnerTestTreeViewProvider {
    WallTimeAwareConsoleProperties(@NotNull Project project) {
      super(new MockRuntimeConfiguration(project), TEST_FRAMEWORK_NAME, DefaultRunExecutor.getRunExecutorInstance());
    }

    @Override
    public @NotNull SMTRunnerTestTreeView createSMTRunnerTestTreeView() {
      return new JavaSMTRunnerTestTreeView();
    }

    @Override
    public SMTRunnerTestTreeViewProvider.CustomizedDurationProvider getCustomizedDurationProvider() {
      return JavaAwareTestConsoleProperties.createCustomizedDurationProvider(this);
    }
  }
}
