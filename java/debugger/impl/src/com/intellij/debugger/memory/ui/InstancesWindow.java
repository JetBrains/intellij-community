// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{new DialogWrapperExitAction("Close", CLOSE_EXIT_CODE)};
  }
}
