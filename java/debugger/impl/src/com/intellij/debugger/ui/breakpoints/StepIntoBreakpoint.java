/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 13, 2006
 */
public class StepIntoBreakpoint extends RunToCursorBreakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint");
  @NotNull
  private final MethodFilter myFilter;

  StepIntoBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, @NotNull MethodFilter filter) {
    super(project, pos, false);
    myFilter = filter;
  }

  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, SourcePosition classPosition) {
    super.createOrWaitPrepare(debugProcess, classPosition);
  }

  protected boolean acceptLocation(DebugProcessImpl debugProcess, ReferenceType classType, Location loc) {
    try {
      return myFilter.locationMatches(debugProcess, loc);
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    return true;
  }

  @Nullable
  protected static StepIntoBreakpoint create(@NotNull Project project, @NotNull MethodFilter filter) {
    final SourcePosition pos = filter.getBreakpointPosition();
    if (pos != null) {
      final StepIntoBreakpoint breakpoint = new StepIntoBreakpoint(project, pos, filter);
      breakpoint.init();
      breakpoint.LOG_ENABLED = false;
      return breakpoint;
    }
    return null;
  }
}
