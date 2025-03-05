// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBDimension;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.memory.ui.InstancesWindowBase;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class InstancesWindow extends InstancesWindowBase {

  private final InstancesView myInstancesView;

  public InstancesWindow(@NotNull XDebugSession session,
                         @NotNull InstancesProvider provider,
                         @NotNull ReferenceType classType) {
    super(session, classType.name());
    myInstancesView = new InstancesView(session, provider, classType,  this::addWarningMessage);
    Disposer.register(this.myDisposable, myInstancesView);
    myInstancesView.setPreferredSize(
      new JBDimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));

    init();
    JRootPane root = myInstancesView.getRootPane();
    root.setDefaultButton(myInstancesView.getFilterButton());
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myInstancesView;
  }

  @Override
  protected @Nullable JComponent createSouthPanel() {
    JComponent comp = super.createSouthPanel();
    if (comp != null) {
      comp.add(myInstancesView.getProgress(), BorderLayout.WEST);
    }
    return comp;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{new DialogWrapperExitAction(CommonBundle.message("action.text.close"), CLOSE_EXIT_CODE)};
  }
}
