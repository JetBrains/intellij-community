/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.debugger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.debugger.impl.DebuggerSession;

/**
 * @author nik
 */
public abstract class HotSwapUI {
  public static HotSwapUI getInstance(Project project) {
    return project.getComponent(HotSwapUI.class);
  }

  public abstract void reloadChangedClasses(DebuggerSession session, boolean compileBeforeHotswap);

  public abstract void dontPerformHotswapAfterThisCompilation();


  public abstract void addListener(HotSwapVetoableListener listener);

  public abstract void removeListener(HotSwapVetoableListener listener);
}
