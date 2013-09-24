package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
  public JavaBreakpointType() {
    super("java", DebuggerBundle.message("java.breakpoint.title"));
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull Project project) {
    return file.getFileType() == JavaFileType.INSTANCE;
  }

  @Nullable
  @Override
  public XBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return null;
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<XBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }
}