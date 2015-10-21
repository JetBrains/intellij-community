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
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SelectTestStep extends BaseListPopupStep<String> {
  private final Map<String, TestStateStorage.Record> myRecords;
  private final RecentTestRunner myRunner;
  
  public SelectTestStep(Map<String, TestStateStorage.Record> records, RecentTestRunner runner) {
    super("Debug Recent Tests", getUrls(records));
    myRunner = runner;
    myRecords = records;
  }

  private static List<String> getUrls(Map<String, TestStateStorage.Record> records) {
    List<String> list = ContainerUtil.newArrayList(records.keySet());
    Collections.sort(list, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        String path1 = VirtualFileManager.extractPath(o1);
        String path2 = VirtualFileManager.extractPath(o2);
        return path1.compareTo(path2);
      }
    });
    return list;
  }

  @NotNull
  @Override
  public String getTextFor(String value) {
    return VirtualFileManager.extractPath(value);
  }
  
  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public Icon getIconFor(String value) {
    TestStateStorage.Record record = myRecords.get(value);
    TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(record.magnitude);
    return TestIconMapper.getIcon(magnitude);
  }
  
  @Override
  public PopupStep onChosen(String url, boolean finalChoice) {
    Location location = myRunner.getLocation(url);
    myRunner.run(location);
    return null;
  }
}
