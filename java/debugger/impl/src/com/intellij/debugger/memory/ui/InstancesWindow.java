/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBDimension;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.memory.ui.InstancesWindowBase;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class InstancesWindow extends InstancesWindowBase {

  private final InstancesView myInstancesView;

  public InstancesWindow(@NotNull XDebugSession session, @NotNull InstancesProvider provider, @NotNull String className) {
    super(session, className);
    myInstancesView = new InstancesView(session, provider, className, this::addWarningMessage);
    Disposer.register(this.myDisposable, myInstancesView);
    myInstancesView.setPreferredSize(
      new JBDimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));

    init();
    JRootPane root = myInstancesView.getRootPane();
    root.setDefaultButton(myInstancesView.getFilterButton());
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myInstancesView;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent comp = super.createSouthPanel();
    if (comp != null) {
      comp.add(myInstancesView.getProgress(), BorderLayout.WEST);
    }
    return comp;
  }


}
