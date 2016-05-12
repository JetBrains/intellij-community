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
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class SelectTestStep extends BaseListPopupStep<TestInfo> {
  private final RecentTestRunner myRunner;
  private final TestLocator myTestLocator;

  public SelectTestStep(List<TestInfo> tests, RecentTestRunner runner, TestLocator locator) {
    super("Debug Recent Tests", tests);
    myRunner = runner;
    myTestLocator = locator;
  }

  @Override
  public Icon getIconFor(TestInfo value) {
    return TestIconMapper.getIcon(value.getMagnitude());
  }

  @NotNull
  @Override
  public String getTextFor(TestInfo value) {
    String url = value.getUrl();
    return VirtualFileManager.extractPath(url);
  }
  
  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }
  
  @Override
  public PopupStep onChosen(TestInfo info, boolean finalChoice) {
    Location location = myTestLocator.getLocation(info.getUrl());
    myRunner.run(location);
    return null;
  }
}
