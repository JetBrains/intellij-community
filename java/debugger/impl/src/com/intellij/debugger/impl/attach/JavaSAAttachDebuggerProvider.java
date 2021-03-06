// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import org.jetbrains.annotations.NotNull;

public class JavaSAAttachDebuggerProvider extends JavaAttachDebuggerProvider {
  private static final XLocalAttachGroup ourAttachGroup = new JavaDebuggerAttachGroup(
    JavaDebuggerBundle.message("debugger.attach.group.name.java.read.only"), -19);

  @NotNull
  @Override
  public XLocalAttachGroup getAttachGroup() {
    return ourAttachGroup;
  }

  @Override
  boolean isDebuggerAttach(LocalAttachInfo info) {
    return !super.isDebuggerAttach(info);
  }
}
