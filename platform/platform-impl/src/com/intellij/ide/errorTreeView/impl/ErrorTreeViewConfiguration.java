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
package com.intellij.ide.errorTreeView.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;


@State(name = "ErrorTreeViewConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ErrorTreeViewConfiguration implements PersistentStateComponent<ErrorTreeViewConfiguration> {
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean HIDE_WARNINGS = false;

  public static ErrorTreeViewConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, ErrorTreeViewConfiguration.class);
  }

  public boolean isAutoscrollToSource() {
    return IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoscrollToSource(boolean autoscroll) {
    IS_AUTOSCROLL_TO_SOURCE = autoscroll;
  }

  public boolean isHideWarnings() {
    return HIDE_WARNINGS;
  }

  public void setHideWarnings(boolean value) {
    HIDE_WARNINGS = value;
  }

  public ErrorTreeViewConfiguration getState() {
    return this;
  }

  public void loadState(final ErrorTreeViewConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
