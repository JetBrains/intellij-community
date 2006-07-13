/*
* Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usageView;

import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;

import javax.swing.*;

public abstract class UsageViewManager {
  public static UsageViewManager getInstance(Project project) {
    return project.getComponent(UsageViewManager.class);
  }

  public abstract Content addContent(String contentName, boolean reusable, final JComponent component, boolean toOpenInNewTab, boolean isLockable);

  public abstract Content addContent(String contentName,
                                     String tabName,
                                     String toolwindowTitle,
                                     boolean reusable,
                                     final JComponent component,
                                     boolean toOpenInNewTab,
                                     boolean isLockable);

  public abstract int getReusableContentsCount();

  public abstract Content getSelectedContent(boolean reusable);

  public abstract Content getSelectedContent();

  public abstract void closeContent(Content usageView);
}