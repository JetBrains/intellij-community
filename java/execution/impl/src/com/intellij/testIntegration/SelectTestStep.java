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
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class SelectTestStep extends BaseListPopupStep<String> {
  private final RecentTestRunner myRunner;
  private final TestLocator myTestLocator;
  private final Map<String, Icon> myIcons;

  public SelectTestStep(List<String> urls, Map<String, Icon> icons, RecentTestRunner runner, TestLocator locator) {
    super("Debug Recent Tests", urls);
    myRunner = runner;
    myIcons = icons;
    myTestLocator = locator;
  }

  @Override
  public Icon getIconFor(String value) {
    return myIcons.get(value);
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
  public PopupStep onChosen(String url, boolean finalChoice) {
    Location location = myTestLocator.getLocation(url);
    myRunner.run(location);
    return null;
  }
}
