// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ViewAsGroup {
  public static @NotNull List<JavaValue> getSelectedValues(@NotNull AnActionEvent event) {
    XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(event);
    if (sessionProxy == null) return Collections.emptyList();
    List<XValueNodeImpl> selectedNodes = XDebuggerTree.getSelectedNodes(event.getDataContext());
    return StreamEx.of(selectedNodes)
      .map(XValueNodeImpl::getValueContainer)
      .map(xValue -> MonolithJavaValueUtilsKt.findJavaValue(xValue, sessionProxy))
      .select(JavaValue.class)
      .toList();
  }
}