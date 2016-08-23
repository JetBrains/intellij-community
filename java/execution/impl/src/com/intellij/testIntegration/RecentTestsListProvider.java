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

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.Map;

import static com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.values;


interface ConfigurationByRecordProvider {
  RunnerAndConfigurationSettings getConfiguration(TestStateStorage.Record record);
}


class RunConfigurationByRecordProvider implements ConfigurationByRecordProvider {
  private final Project myProject;
  private final Map<Integer, RunnerAndConfigurationSettings> myConfigurationsMap = ContainerUtil.newHashMap();
  
  public RunConfigurationByRecordProvider(Project project) {
    myProject = project;
    initRunConfigurationsMap();
  }
  
  @Override
  public RunnerAndConfigurationSettings getConfiguration(TestStateStorage.Record record) {
    Integer runConfigurationHash = new Integer((int)record.configurationHash);
    return myConfigurationsMap.get(runConfigurationHash);
  }

  private void initRunConfigurationsMap() {
    RunManagerEx manager = RunManagerEx.getInstanceEx(myProject);
    ConfigurationType[] types = manager.getConfigurationFactories();

    for (ConfigurationType type : types) {
      Map<String, List<RunnerAndConfigurationSettings>> structure = manager.getStructure(type);
      for (Map.Entry<String, List<RunnerAndConfigurationSettings>> e : structure.entrySet()) {
        for (RunnerAndConfigurationSettings settings : e.getValue()) {
          myConfigurationsMap.put(settings.getName().hashCode(), settings);
        }
      }
    }
  }
  
}


public class RecentTestsListProvider {
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