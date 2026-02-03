// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeBackendOnlyActionBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ViewAsGroup {
  public static @NotNull List<JavaValue> getSelectedValues(@NotNull AnActionEvent event) {
    XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(event);
    if (sessionProxy == null) return Collections.emptyList();
    var selectedValues = XDebuggerTreeBackendOnlyActionBase.getSelectedValues(event.getDataContext());
    return ContainerUtil.filterIsInstance(selectedValues, JavaValue.class);
  }
}