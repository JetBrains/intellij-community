/*
 * Class AnyExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.sun.jdi.ReferenceType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class AnyExceptionBreakpoint extends ExceptionBreakpoint {
  public static final @NonNls String ANY_EXCEPTION_BREAKPOINT = "breakpoint_any";

  protected AnyExceptionBreakpoint(Project project) {
    super(project, null, null);
    ENABLED = false;
  }

  public String getCategory() {
    return ANY_EXCEPTION_BREAKPOINT;
  }

  public String getDisplayName() {
    return DebuggerBundle.message("breakpoint.any.exception.display.name");
  }

  public void createRequest(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!ENABLED || !debugProcess.isAttached() || debugProcess.areBreakpointsMuted() || !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
      return;
    }
    super.processClassPrepare(debugProcess, null);
  }

  public void processClassPrepare(DebugProcess debugProcess, ReferenceType refType) {
    // should be emty - does not make sense for this breakpoint
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    try {
      super.readExternal(parentNode);
    }
    catch (InvalidDataException e) {
      if(!READ_NO_CLASS_NAME.equals(e.getMessage())) throw e;
    }
  }

}