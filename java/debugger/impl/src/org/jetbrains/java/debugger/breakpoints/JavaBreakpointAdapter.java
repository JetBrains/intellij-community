package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.jetbrains.annotations.NotNull;

public class JavaBreakpointAdapter extends JavaBreakpointAdapterBase {
  private static final Key<LineBreakpoint> OLD_JAVA_BREAKPOINT_KEY = Key.create("oldJavaBreakpoint");

  public JavaBreakpointAdapter(Project project) {
    super(project);
  }

  @Override
  protected void configureCreatedBreakpoint(LineBreakpoint oldBreakpoint, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    oldBreakpoint.SUSPEND_POLICY = transformSuspendPolicy(breakpoint);
    applyCondition(oldBreakpoint, breakpoint);
  }

  private static void applyCondition(LineBreakpoint oldBreakpoint, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    if (breakpoint.getCondition() != null) {
      oldBreakpoint.CONDITION_ENABLED = true;
      oldBreakpoint.setCondition(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, breakpoint.getCondition()));
    }
    else {
      oldBreakpoint.CONDITION_ENABLED = false;
      if (!StringUtil.isEmptyOrSpaces(oldBreakpoint.getCondition().getText())) {
        oldBreakpoint.setCondition(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
      }
    }
  }

  @Override
  protected void updateBreakpoint(LineBreakpoint jBreakpoint, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    boolean changed = false;
    if (jBreakpoint.ENABLED != breakpoint.isEnabled()) {
      jBreakpoint.ENABLED = breakpoint.isEnabled();
      changed = true;
    }

    String suspendPolicy = transformSuspendPolicy(breakpoint);
    if (jBreakpoint.SUSPEND_POLICY != suspendPolicy) {
      jBreakpoint.SUSPEND_POLICY = suspendPolicy;
      changed = true;
    }

    if (StringUtil.compare(breakpoint.getCondition(), jBreakpoint.getCondition().getText(), false) != 0) {
      applyCondition(jBreakpoint, breakpoint);
      changed = true;
    }

    if (jBreakpoint.getSourcePosition().getLine() != breakpoint.getLine()) {
      jBreakpoint.reload();
      changed = true;
    }

    if (changed) {
      RequestManagerImpl.updateRequests(jBreakpoint);
      jBreakpoint.updateUI();
    }
  }

  @Override
  protected LineBreakpoint findBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    return OLD_JAVA_BREAKPOINT_KEY.get(breakpoint);
  }

  public LineBreakpoint getOrCreate(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    LineBreakpoint oldBreakpoint = findBreakpoint(breakpoint);
    if (oldBreakpoint == null) {
      oldBreakpoint = createBreakpoint(breakpoint);
      OLD_JAVA_BREAKPOINT_KEY.set(breakpoint, oldBreakpoint);
    }
    return oldBreakpoint;
  }

  @Override
  public void breakpointRemoved(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint) {
    LineBreakpoint jBreakpoint = findBreakpoint(breakpoint);
    if (jBreakpoint != null) {
      jBreakpoint.delete();
    }
  }

  @Override
  protected LineBreakpoint doCreateInstance(Project project, Document document, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    LineBreakpoint lineBreakpoint = new LineBreakpoint(project, ((XLineBreakpointImpl)breakpoint).getHighlighter()) {
      @Override
      protected void setEditorFilter(RangeHighlighter highlighter) {
      }
    };

    lineBreakpoint.init();
    return lineBreakpoint;
  }

  private static String transformSuspendPolicy(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    switch (breakpoint.getSuspendPolicy()) {
      case ALL:
        return DebuggerSettings.SUSPEND_ALL;
      case THREAD:
        return DebuggerSettings.SUSPEND_THREAD;
      case NONE:
        return DebuggerSettings.SUSPEND_NONE;

      default:
        throw new IllegalArgumentException("unknown suspend policy");
    }
  }
}
