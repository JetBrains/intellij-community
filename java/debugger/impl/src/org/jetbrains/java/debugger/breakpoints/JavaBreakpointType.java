package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import java.util.List;

public class JavaBreakpointType extends XLineBreakpointTypeBase {
  public JavaBreakpointType() {
    super("java", DebuggerBundle.message("java.breakpoint.title"), new JavaDebuggerEditorsProvider());
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull Project project) {
    return file.getFileType() == JavaFileType.INSTANCE;
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<XBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }
}