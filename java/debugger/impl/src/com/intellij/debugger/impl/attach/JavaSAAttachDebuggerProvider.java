// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.xdebugger.attach.XAttachPresentationGroup;
import org.jetbrains.annotations.NotNull;

public final class JavaSAAttachDebuggerProvider extends JavaAttachDebuggerProvider {
  private static final XAttachPresentationGroup<ProcessInfo> ourAttachGroup = new JavaDebuggerAttachGroup(
    JavaDebuggerBundle.message("debugger.attach.group.name.java.read.only"), -19);

  @Override
  public @NotNull XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return ourAttachGroup;
  }

  @Override
  boolean isDebuggerAttach(LocalAttachInfo info) {
    return !super.isDebuggerAttach(info);
  }
}
