// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class HotSwapUI {
  public static HotSwapUI getInstance(Project project) {
    return project.getService(HotSwapUI.class);
  }

  public abstract void reloadChangedClasses(@NotNull DebuggerSession session, boolean compileBeforeHotswap);

  public abstract void reloadChangedClasses(@NotNull DebuggerSession session, boolean compileBeforeHotswap,
                                            @Nullable HotSwapStatusListener callback);

  public abstract void compileAndReload(@NotNull DebuggerSession session, VirtualFile @NotNull ... files);

  public abstract void addListener(HotSwapVetoableListener listener);

  public abstract void removeListener(HotSwapVetoableListener listener);
}
