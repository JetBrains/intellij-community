/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class RunToCursorBreakpoint extends SyntheticLineBreakpoint {
  private final boolean myRestoreBreakpoints;
  @NotNull
  protected final SourcePosition myCustomPosition;

  protected RunToCursorBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, boolean restoreBreakpoints) {
    super(project);
    myCustomPosition = pos;
    myRestoreBreakpoints = restoreBreakpoints;
  }

  @NotNull
  @Override
  public SourcePosition getSourcePosition() {
    return myCustomPosition;
  }

  @Override
  public int getLineIndex() {
    return myCustomPosition.getLine();
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    return DebuggerBundle.message("status.stopped.at.cursor");
  }

  @Nullable
  @Override
  protected JavaLineBreakpointType getXBreakpointType() {
    SourcePosition position = getSourcePosition();
    VirtualFile file = position.getFile().getVirtualFile();
    int line = position.getLine();
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      if (type instanceof JavaLineBreakpointType && type.canPutAt(file, line, myProject)) {
        return ((JavaLineBreakpointType)type);
      }
    }
    return null;
  }

  @Nullable
  protected static RunToCursorBreakpoint create(@NotNull Project project, @NotNull XSourcePosition position, boolean restoreBreakpoints) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(position.getFile());
    if (psiFile == null) {
      return null;
    }
    return new RunToCursorBreakpoint(project, SourcePosition.createFromOffset(psiFile, position.getOffset()), restoreBreakpoints);
  }
}
