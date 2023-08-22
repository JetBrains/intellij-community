// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.List;
import java.util.Map;

import static com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.values;

interface ConfigurationByRecordProvider {
  RunnerAndConfigurationSettings getConfiguration(TestStateStorage.Record record);
}

final class RunConfigurationByRecordProvider implements ConfigurationByRecordProvider {
  private final Project myProject;
  private final Int2ObjectMap<RunnerAndConfigurationSettings> myConfigurationsMap = new Int2ObjectOpenHashMap<>();

  RunConfigurationByRecordProvider(Project project) {
    myProject = project;
    initRunConfigurationsMap();
  }

  @Override
  public RunnerAndConfigurationSettings getConfiguration(TestStateStorage.Record record) {
    return myConfigurationsMap.get((int)record.configurationHash);
  }

  private void initRunConfigurationsMap() {
    for (RunnerAndConfigurationSettings settings : RunManagerImpl.getInstanceImpl(myProject).getAllSettings()) {
      myConfigurationsMap.put(settings.getName().hashCode(), settings);
    }
  }
}

public final class RecentTestsListProvider {
  private final Map<String, TestStateStorage.Record> myRecords;
  private final ConfigurationByRecordProvider myConfigurationProvider;

  public RecentTestsListProvider(ConfigurationByRecordProvider configurationProvider, Map<String, TestStateStorage.Record> records) {
    myRecords = records;
    myConfigurationProvider = configurationProvider;
  }

  public List<RecentTestsPopupEntry> getTestsToShow() {
    if (myRecords == null) return ContainerUtil.emptyList();

    RecentTestsData data = new RecentTestsData();
    for (Map.Entry<String, TestStateStorage.Record> entry : myRecords.entrySet()) {
      String url = entry.getKey();
      TestStateStorage.Record record = entry.getValue();

      if (TestLocator.canLocate(url)) {
        handleUrl(data, url, record);
      }
    }

    return data.getTestsToShow();
  }

  private void handleUrl(RecentTestsData data, String url, TestStateStorage.Record record) {
    TestStateInfo.Magnitude magnitude = getMagnitude(record.magnitude);
    if (magnitude == null) {
      return;
    }

    RunnerAndConfigurationSettings runConfiguration = myConfigurationProvider.getConfiguration(record);
    if (runConfiguration == null) {
      return;
    }

    if (TestLocator.isSuite(url)) {
      SuiteEntry entry = new SuiteEntry(url, record.date, runConfiguration);
      data.addSuite(entry);
    }
    else {
      SingleTestEntry entry = new SingleTestEntry(url, record.date, runConfiguration, magnitude);
      data.addTest(entry);
    }
  }

  private static TestStateInfo.Magnitude getMagnitude(int magnitude) {
    for (TestStateInfo.Magnitude m : values()) {
      if (m.getValue() == magnitude) {
        return m;
      }
    }
    return null;
  }
}