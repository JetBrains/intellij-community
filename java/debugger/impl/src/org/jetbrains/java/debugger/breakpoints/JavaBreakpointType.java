package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SystemProperties;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import java.util.List;

public class JavaBreakpointType extends XLineBreakpointTypeBase {
  public JavaBreakpointType() {
    super("java", DebuggerBundle.message("java.breakpoint.title"), new JavaDebuggerEditorsProvider());
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull Project project) {
    return SystemProperties.getBooleanProperty("java.debugger.xBreakpoint", false) &&
           doCanPutAt(PsiManager.getInstance(project).findFile(file));
  }

  @Override
  public boolean isSuspendThreadSupported() {
    return true;
  }

  @Override
  public List<XBreakpointGroupingRule<XLineBreakpoint<XBreakpointProperties>, ?>> getGroupingRules() {
    return XDebuggerUtil.getInstance().getGroupingByFileRuleAsList();
  }

  @Contract("null -> false")
  public static boolean doCanPutAt(@Nullable PsiFile psiFile) {
    // JSPX supports jvm debugging, but not in XHTML files
    if (psiFile == null || psiFile.getVirtualFile().getFileType() == StdFileTypes.XHTML) {
      return false;
    }

    FileType fileType = psiFile.getFileType();
    return StdFileTypes.CLASS.equals(fileType) || DebuggerUtils.supportsJVMDebugging(fileType) || DebuggerUtils.supportsJVMDebugging(psiFile);
  }

  @Nullable
  @Override
  public XBreakpointProperties createProperties() {
    return new JavaBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return new JavaBreakpointProperties();
  }

  @Nullable
  @Override
  public XBreakpointCustomPropertiesPanel<XLineBreakpoint<XBreakpointProperties>> createCustomRightPropertiesPanel(@NotNull Project project) {
    return new JavaBreakpointPropertiesPanel(project);
  }


}