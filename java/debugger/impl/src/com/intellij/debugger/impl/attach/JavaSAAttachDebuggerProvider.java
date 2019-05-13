// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.xdebugger.attach.XLocalAttachGroup;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class JavaSAAttachDebuggerProvider extends JavaAttachDebuggerProvider {
  private static final XLocalAttachGroup ourAttachGroup = new JavaDebuggerAttachGroup("Java Read Only", -19);

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
