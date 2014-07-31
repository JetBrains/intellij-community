/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Class AnyExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.ReferenceType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

public class AnyExceptionBreakpoint extends ExceptionBreakpoint {
  public static final @NonNls Key<AnyExceptionBreakpoint> ANY_EXCEPTION_BREAKPOINT = BreakpointCategory.lookup("breakpoint_any");

  protected AnyExceptionBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint) {
    super(project, null, null, xBreakpoint);
    //setEnabled(false);
  }

  public Key<AnyExceptionBreakpoint> getCategory() {
    return ANY_EXCEPTION_BREAKPOINT;
  }

  public String getDisplayName() {
    return DebuggerBundle.message("breakpoint.any.exception.display.name");
  }

  public void createRequest(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!shouldCreateRequest(debugProcess)) {
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