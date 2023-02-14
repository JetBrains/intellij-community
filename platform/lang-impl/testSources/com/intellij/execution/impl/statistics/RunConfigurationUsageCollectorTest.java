// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerConfig;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.impl.statistics.BaseTestConfigurationFactory.FirstBaseTestConfigurationFactory;
import com.intellij.execution.impl.statistics.BaseTestConfigurationFactory.MultiFactoryLocalTestConfigurationFactory;
import com.intellij.execution.impl.statistics.BaseTestConfigurationFactory.MultiFactoryRemoteTestConfigurationFactory;
import com.intellij.execution.impl.statistics.BaseTestConfigurationFactory.SecondBaseTestConfigurationFactory;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class RunConfigurationUsageCollectorTest extends LightPlatformTestCase {

  private void doTest(@NotNull List<? extends RunnerAndConfigurationSettings> configurations,
                      @NotNull Set<TestUsageDescriptor> expected, boolean withTemporary) {
    final Project project = getProject();
    final RunManager manager = RunManager.getInstance(project);
    try {
      for (RunnerAndConfigurationSettings configuration : configurations) {
        manager.addConfiguration(configuration);
      }

      final RunConfigurationTypeUsagesCollector collector = new RunConfigurationTypeUsagesCollector();

      final Set<MetricEvent> usages;
      try {
        usages = collector.getMetrics(project, new EmptyProgressIndicator()).get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      assertEquals(expected.size(), usages.size());
      assertEquals(expected, toTestUsageDescriptor(usages));

      if (withTemporary) {
        for (RunnerAndConfigurationSettings configuration : configurations) {
          configuration.setTemporary(true);
        }

        final Set<TestUsageDescriptor> temporaryExpected = new HashSet<>();
        for (TestUsageDescriptor descriptor : expected) {
          if (descriptor.myKey.equals("configured.in.project")) {
            final FeatureUsageData data = descriptor.myData.copy().addData("temporary", true);
            temporaryExpected.add(new TestUsageDescriptor(descriptor.myKey, data));
          }
        }
        temporaryExpected.add(createTotalCountUsageDescriptor(
          configurations.size(),
          (int)configurations.stream().filter(settings -> settings.isTemporary()).count()));

        final Set<MetricEvent> temporaryUsages;
        try {
          temporaryUsages = collector.getMetrics(project, new EmptyProgressIndicator()).get();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
        assertEquals(temporaryExpected.size(), temporaryUsages.size());
        final Set<TestUsageDescriptor> actual = toTestUsageDescriptor(temporaryUsages);
        assertEquals(temporaryExpected, actual);
      }
    }
    finally {
      for (RunnerAndConfigurationSettings configuration : configurations) {
        manager.removeConfiguration(configuration);
      }
      RunManagerConfig config = new RunManagerConfig(PropertiesComponent.getInstance());
      config.setRecentsLimit(RunManagerConfig.DEFAULT_RECENT_LIMIT);
    }
  }

  public void testSingleRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  public void testMultipleRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, false, false));
    configurations.add(createFirst(instance, 3, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 3,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(3, 0));
    doTest(configurations, expected, true);
  }

  public void testSharedRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, true, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", true, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, false);
  }

  public void testEditBeforeRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, true, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, true, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  public void testActivateRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, true, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, true, false))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  public void testParallelRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, true));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, true))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  public void testDifferentRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));
    configurations.add(createSecond(instance, 2, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("SecondTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(2, 0));
    doTest(configurations, expected, true);
  }

  public void testRunConfigurationsWithDifferentShared() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, true, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, false, false));
    configurations.add(createFirst(instance, 3, true, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", true, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(3, 0));
    doTest(configurations, expected, false);
  }

  public void testRunConfigurationsWithDifferentEditBeforeRun() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, true, false, false));
    configurations.add(createFirst(instance, 2, false, true, false, false));
    configurations.add(createFirst(instance, 3, false, false, false, false));
    configurations.add(createFirst(instance, 4, false, true, false, false));
    configurations.add(createFirst(instance, 5, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 3,
      create("FirstTestRunConfigurationType", false, true, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(5, 0));
    doTest(configurations, expected, true);
  }

  public void testRunConfigurationsWithDifferentActivate() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, true, false));
    configurations.add(createFirst(instance, 3, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, true, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(3, 0));
    doTest(configurations, expected, false);
  }

  public void testRunConfigurationsWithDifferentParallel() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, false, true));
    configurations.add(createFirst(instance, 3, false, false, false, true));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", false, false, false, true))
    );
    expected.add(createTotalCountUsageDescriptor(3, 0));
    doTest(configurations, expected, false);
  }

  public void testMultipleDifferentRunConfiguration() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, false, false));
    configurations.add(createSecond(instance, 3, false, false, false, false));
    configurations.add(createSecond(instance, 4, false, false, false, false));
    configurations.add(createSecond(instance, 5, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 3,
      create("SecondTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(5, 0));
    doTest(configurations, expected, true);
  }

  public void testDifferentRunConfigurationWithDifferentContext() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, true, false, false, false));
    configurations.add(createFirst(instance, 2, false, false, false, false));
    configurations.add(createFirst(instance, 3, true, true, false, false));
    configurations.add(createFirst(instance, 4, true, true, false, false));
    configurations.add(createFirst(instance, 5, true, true, true, true));
    configurations.add(createSecond(instance, 6, true, false, false, false));
    configurations.add(createSecond(instance, 7, false, false, true, false));
    configurations.add(createSecond(instance, 8, false, false, true, false));
    configurations.add(createSecond(instance, 9, false, false, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", true, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", false, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", true, true, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("FirstTestRunConfigurationType", true, true, true, true))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("SecondTestRunConfigurationType", true, false, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("SecondTestRunConfigurationType", false, false, true, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("SecondTestRunConfigurationType", false, false, false, false))
    );
    expected.add(createTotalCountUsageDescriptor(9, 0));
    doTest(configurations, expected, false);
  }

  public void testDifferentRunConfigurationWithDifferentContextNotShared() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    configurations.add(createFirst(instance, 1, false, false, false, true));
    configurations.add(createFirst(instance, 2, false, false, false, true));
    configurations.add(createSecond(instance, 3, false, false, false, true));
    configurations.add(createSecond(instance, 4, false, true, false, false));
    configurations.add(createSecond(instance, 5, false, true, true, true));
    configurations.add(createSecond(instance, 6, false, true, false, false));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("FirstTestRunConfigurationType", false, false, false, true))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("SecondTestRunConfigurationType", false, false, false, true))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 2,
      create("SecondTestRunConfigurationType", false, true, false, false))
    );
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("SecondTestRunConfigurationType", false, true, true, true))
    );
    expected.add(createTotalCountUsageDescriptor(6, 0));
    doTest(configurations, expected, true);
  }

  public void testRunConfigurationWithLocalFactory() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    final MultiFactoryLocalTestConfigurationFactory factory = MultiFactoryLocalTestConfigurationFactory.INSTANCE;
    configurations.add(createByFactory(instance, factory, 1, false, false, false, true));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("MultiFactoryTestRunConfigurationType", false, false, false, true).addData("factory", "Local"))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  public void testRunConfigurationWithRemoteFactory() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());
    final MultiFactoryRemoteTestConfigurationFactory factory = MultiFactoryRemoteTestConfigurationFactory.INSTANCE;
    configurations.add(createByFactory(instance, factory, 1, false, false, false, true));

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    expected.add(new TestUsageDescriptor(
      "configured.in.project", 1,
      create("MultiFactoryTestRunConfigurationType", false, false, false, true).addData("factory", "Remote"))
    );
    expected.add(createTotalCountUsageDescriptor(1, 0));
    doTest(configurations, expected, true);
  }

  private void testRunConfigurationCount(final int countOne, final int countTwo, final int countTemp,
                                         final int expectedCount, final int expectedTempCount) {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<>();
    final RunManager instance = RunManager.getInstance(getProject());

    for (int i = 1; i <= countOne; i ++) {
      final RunnerAndConfigurationSettings first = createFirst(instance, i, false, false, false, false);
      configurations.add(first);
    }
    for (int i = countOne + 1; i <= countOne + countTwo; i ++) {
      final RunnerAndConfigurationSettings second = createSecond(instance, i, false, false, false, false);
      configurations.add(second);
    }
    for (int i = countOne + countTwo + 1; i <= countOne + countTwo + countTemp; i ++) {
      final RunnerAndConfigurationSettings temp = createFirst(instance, i, false, false, false, false);
      temp.setTemporary(true);
      configurations.add(temp);
    }

    final Set<TestUsageDescriptor> expected = new HashSet<>();
    if (countOne > 0) {
      expected.add(new TestUsageDescriptor(
        "configured.in.project", countOne,
        create("FirstTestRunConfigurationType", false, false, false, false))
      );
    }
    if (countTwo > 0) {
      expected.add(new TestUsageDescriptor(
        "configured.in.project", countTwo,
        create("SecondTestRunConfigurationType", false, false, false, false))
      );
    }
    if (countTemp > 0) {
      expected.add(new TestUsageDescriptor(
        "configured.in.project", expectedTempCount,
        create("FirstTestRunConfigurationType", false, false, false, false, true))
      );
    }
    expected.add(createTotalCountUsageDescriptor(expectedCount, expectedTempCount));
    doTest(configurations, expected, false);
  }

  public void testRunConfigurationCountEmpty() {
    testRunConfigurationCount(0, 0, 0, 0, 0);
  }

  public void testRunConfigurationCount() {
    testRunConfigurationCount(256, 0, 0, 256, 0);
  }

  public void testRunConfigurationCountTwo() {
    testRunConfigurationCount(200, 56, 0, 256, 0);
  }

  public void testRunConfigurationMaxCount() {
    testRunConfigurationCount(550, 0, 0, 500, 0);
  }

  public void testRunConfigurationMaxCountTwo() {
    testRunConfigurationCount(500, 50, 0, 500, 0);
  }

  public void testRunConfigurationOnlyTemporary() {
    testRunConfigurationCount(0, 0, 10, 5, 5);
  }

  public void testRunConfigurationOnlyTemporaryCustomLimit() {
    RunManagerConfig config = new RunManagerConfig(PropertiesComponent.getInstance());
    config.setRecentsLimit(15);
    testRunConfigurationCount(0, 0, 15, 15, 15);
  }

  public void testRunConfigurationMixWithTemporary() {
    testRunConfigurationCount(10, 10, 10, 25, 5);
  }

  public void testRunConfigurationMixWithTemporaryCustomLimit() {
    RunManagerConfig config = new RunManagerConfig(PropertiesComponent.getInstance());
    config.setRecentsLimit(15);
    testRunConfigurationCount(10, 10, 10, 30, 10);
  }

  public void testRunConfigurationMixWithTemporaryMaxCount() {
    testRunConfigurationCount(300, 100, 50, 405, 5);
  }

  public void testRunConfigurationMixWithTemporaryMaxCountCustomLimit() {
    RunManagerConfig config = new RunManagerConfig(PropertiesComponent.getInstance());
    config.setRecentsLimit(501);
    testRunConfigurationCount(200, 300, 501, 500, 501);
  }

  @NotNull
  private static FeatureUsageData create(@NotNull String id, boolean isShared, boolean isEditBeforeRun, boolean isActivate, boolean isParallel) {
    return create(id, isShared, isEditBeforeRun, isActivate, isParallel, false);
  }

  @NotNull
  private static FeatureUsageData create(@NotNull String id, boolean isShared, boolean isEditBeforeRun,
                                         boolean isActivate, boolean isParallel, boolean temporary) {
    return new FeatureUsageData().
      addData("id", id).
      addData("edit_before_run", isEditBeforeRun).
      addData("activate_before_run", isActivate).
      addData("shared", isShared).
      addData("parallel", isParallel).
      addData("temporary", temporary);
  }

  private static FeatureUsageData createTotalCountData(int totalCount, int tempCount) {
    return new FeatureUsageData().addData("total_count", totalCount).addData("temp_count", tempCount);
  }

  private static TestUsageDescriptor createTotalCountUsageDescriptor(int totalCount, int tempCount) {
    return new TestUsageDescriptor(
      "total.configurations.registered",
      createTotalCountData(Math.min(totalCount, 500), tempCount));
  }

  @NotNull
  private static Set<TestUsageDescriptor> toTestUsageDescriptor(@NotNull Set<MetricEvent> descriptors) {
    final Set<TestUsageDescriptor> result = new HashSet<>();
    for (MetricEvent descriptor : descriptors) {
      result.add(new TestUsageDescriptor(descriptor));
    }
    return result;
  }

  private static final class TestUsageDescriptor {
    private final String myKey;
    private final FeatureUsageData myData;

    private TestUsageDescriptor(@NotNull String key, int value, @NotNull FeatureUsageData data) {
      this(key, data.addData("count", value));
    }

    private TestUsageDescriptor(@NotNull String key, @NotNull FeatureUsageData data) {
      myKey = key;
      myData = data;
    }

    private TestUsageDescriptor(@NotNull MetricEvent descriptor) {
      myKey = descriptor.getEventId();
      myData = descriptor.getData();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TestUsageDescriptor that = (TestUsageDescriptor)o;

      return Objects.equals(myKey, that.myKey) &&
             Objects.equals(myData, that.myData);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myKey, myData);
    }

    @Override
    public String toString() {
      return "'" + myKey + "' " + myData.build();
    }
  }

  private static RunnerAndConfigurationSettings createFirst(@NotNull RunManager manager, int index,
                                                            boolean isShared, boolean isEditBeforeRun,
                                                            boolean isActivate, boolean isParallel) {
    return createByFactory(manager, FirstBaseTestConfigurationFactory.INSTANCE, index, isShared, isEditBeforeRun, isActivate, isParallel);

  }

  private static RunnerAndConfigurationSettings createSecond(@NotNull RunManager manager, int index,
                                                             boolean isShared, boolean isEditBeforeRun,
                                                             boolean isActivate, boolean isParallel) {
    return createByFactory(manager, SecondBaseTestConfigurationFactory.INSTANCE, index, isShared, isEditBeforeRun, isActivate, isParallel);
  }

  private static RunnerAndConfigurationSettings createByFactory(@NotNull RunManager manager, @NotNull ConfigurationFactory factory,
                                                                int index, boolean isShared, boolean isEditBeforeRun,
                                                                boolean isActivate, boolean isParallel) {
    return configure(manager.createConfiguration("Test_" + index, factory), isShared, isEditBeforeRun, isActivate, isParallel);
  }

  @NotNull
  private static RunnerAndConfigurationSettings configure(@NotNull RunnerAndConfigurationSettings configuration,
                                                          boolean isShared, boolean isEditBeforeRun,
                                                          boolean isActivate, boolean isParallel) {
    if (isShared) {
      configuration.storeInDotIdeaFolder();
    }
    else {
      configuration.storeInLocalWorkspace();
    }
    configuration.setEditBeforeRun(isEditBeforeRun);
    configuration.setActivateToolWindowBeforeRun(isActivate);
    configuration.getConfiguration().setAllowRunningInParallel(isParallel);
    return configuration;
  }
}
