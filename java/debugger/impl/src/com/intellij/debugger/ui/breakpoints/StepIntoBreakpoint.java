/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class StepIntoBreakpoint extends RunToCursorBreakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint");
  @NotNull private final BreakpointStepMethodFilter myFilter;
  @Nullable private RequestHint myHint;

  protected StepIntoBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, @NotNull BreakpointStepMethodFilter filter) {
    super(project, pos, false);
    myFilter = filter;
  }

  protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
    try {
      final CompoundPositionManager positionManager = debugProcess.getPositionManager();
      List<Location> locations = positionManager.locationsOfLine(classType, myCustomPosition);

      if (locations.isEmpty()) {
        // sometimes first statements are mapped to some weird line number, or there are no executable instructions at first statement's line
        // so if lambda or method body spans for more than one lines, try get some locations from these lines
        final int lastLine = myFilter.getLastStatementLine();
        if (lastLine >= 0) {
          int nextLine = myCustomPosition.getLine() + 1;
          while (nextLine <= lastLine && locations.isEmpty()) {
            locations = positionManager.locationsOfLine(classType, SourcePosition.createFromLine(myCustomPosition.getFile(), nextLine++));
          }
        }
      }

      if (!locations.isEmpty()) {
        MultiMap<Method, Location> methods = new MultiMap<>();
        for (Location loc : locations) {
          if (acceptLocation(debugProcess, classType, loc)) {
            methods.putValue(loc.method(), loc);
          }
        }
        Location location = null;
        final int methodsFound = methods.size();
        if (methodsFound == 1) {
          location = methods.values().iterator().next();
        }
        else {
          if (myFilter instanceof LambdaMethodFilter) {
            final LambdaMethodFilter lambdaFilter = (LambdaMethodFilter)myFilter;
            if (lambdaFilter.getLambdaOrdinal() < methodsFound) {
              Method[] candidates = methods.keySet().toArray(new Method[methodsFound]);
              Arrays.sort(candidates, DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR);
              location = methods.get(candidates[lambdaFilter.getLambdaOrdinal()]).iterator().next();
            }
          }
          else {
            if (methodsFound > 0) {
              location = methods.values().iterator().next();
            }
          }
        }
        createLocationBreakpointRequest(this, location, debugProcess);
      }
    }
    catch (ClassNotPreparedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ClassNotPreparedException: " + ex.getMessage());
      }
    }
    catch (ObjectCollectedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ObjectCollectedException: " + ex.getMessage());
      }
    }
    catch(Exception ex) {
      LOG.info(ex);
    }
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
  protected static StepIntoBreakpoint create(@NotNull Project project, @NotNull BreakpointStepMethodFilter filter) {
    final SourcePosition pos = filter.getBreakpointPosition();
    if (pos != null) {
      final StepIntoBreakpoint breakpoint = new StepIntoBreakpoint(project, pos, filter);
      breakpoint.init();
      return breakpoint;
    }
    return null;
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    boolean res = super.processLocatableEvent(action, event);
    if (res && myHint != null && myHint.isResetIgnoreFilters()) {
      SuspendContextImpl context = action.getSuspendContext();
      if (context != null) {
        DebugProcessImpl process = context.getDebugProcess();
        process.checkPositionNotFiltered(context.getThread(), f -> process.getSession().resetIgnoreStepFiltersFlag());
      }
    }
    return res;
  }

  public void setRequestHint(RequestHint hint) {
    myHint = hint;
  }
}
