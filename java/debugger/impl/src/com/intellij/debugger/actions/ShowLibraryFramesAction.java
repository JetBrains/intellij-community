/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;

/**
 * @author egor
 */
public class ShowLibraryFramesAction extends ToggleAction {
  private volatile boolean myShouldShow;
  private static final String ourTextWhenShowIsOn = "Hide Frames from Libraries";
  private static final String ourTextWhenShowIsOff = "Show All Frames";

  public ShowLibraryFramesAction() {
    super("", "", AllIcons.Debugger.Class_filter);
    myShouldShow = DebuggerSettings.getInstance().SHOW_LIBRARY_STACKFRAMES;
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    final boolean shouldShow = !(Boolean)presentation.getClientProperty(SELECTED_PROPERTY);
    presentation.setText(shouldShow ? ourTextWhenShowIsOn : ourTextWhenShowIsOff);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return !myShouldShow;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean enabled) {
    myShouldShow = !enabled;
    DebuggerSettings.getInstance().SHOW_LIBRARY_STACKFRAMES = myShouldShow;
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
  }
}