// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendOtherThreadsRequestor;
import com.intellij.debugger.engine.requests.CustomProcessingLocatableEventRequestor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author Eugene Zhuravlev
 */
public class RunToCursorBreakpoint extends SyntheticLineBreakpoint implements SteppingBreakpoint, CustomProcessingLocatableEventRequestor {
  private final boolean myRestoreBreakpoints;
  @NotNull
  protected final SourcePosition myCustomPosition;
  private final boolean myNeedReplaceWithAllThreadSuspendContext;

  protected RunToCursorBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, boolean restoreBreakpoints, boolean needReplaceWithAllThreadSuspendContext) {
    super(project);
    myCustomPosition = pos;
    myRestoreBreakpoints = restoreBreakpoints;
    myNeedReplaceWithAllThreadSuspendContext = needReplaceWithAllThreadSuspendContext;
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

  @Override
  protected String getFileName() {
    return myCustomPosition.getFile().getName();
  }

  @Override
  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    return JavaDebuggerBundle.message("status.stopped.at.cursor");
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
  protected static RunToCursorBreakpoint create(@NotNull Project project,
                                                @NotNull XSourcePosition position,
                                                boolean restoreBreakpoints,
                                                boolean needReplaceWithAllThreadSuspendContext) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(position.getFile());
    if (psiFile == null) {
      return null;
    }
    return new RunToCursorBreakpoint(project, SourcePosition.createFromOffset(psiFile, position.getOffset()), restoreBreakpoints, needReplaceWithAllThreadSuspendContext);
  }

  @Override
  public void setRequestHint(RequestHint hint) {
  }

  @Override
  public boolean track() {
    return false;
  }

  @Override
  public boolean customVoteSuspend(@NotNull SuspendContextImpl suspendContext) {
    // Now myNeedReplaceWithAllThreadSuspendContext field is initialized from the code we create run-to-cursor command (even in EDT).
    // So we can't know for sure at that moment whether we have a coroutine job and will need suspend-all switch.
    // Then just in case myNeedReplaceWithAllThreadSuspendContext is set to true, so we need to check here the incoming suspend mode.
    // It may be nice to refactor it to calculate the presence of coroutine job in the moment of this breakpoint creation.
    if (!myNeedReplaceWithAllThreadSuspendContext || suspendContext.getSuspendPolicy() != EventRequest.SUSPEND_EVENT_THREAD) return false;
    return SuspendOtherThreadsRequestor.initiateTransferToSuspendAll(suspendContext, c -> true);
  }

  @Override
  public @Nullable Function<SuspendContextImpl, Boolean> applyAfterContextSwitch() {
    return null; // in DebuggerUtils#isAlwaysSuspendThreadBeforeSwitch mode the debugger engine should just use the standard switch
  }
}
