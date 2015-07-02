/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.debugger.engine.BreakpointStepMethodFilter;
import com.intellij.debugger.engine.CompoundPositionManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.LambdaMethodFilter;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.*;
import com.sun.jdi.request.BreakpointRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 13, 2006
 */
public class StepIntoBreakpoint extends RunToCursorBreakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint");
  @NotNull
  private final BreakpointStepMethodFilter myFilter;

  StepIntoBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, @NotNull BreakpointStepMethodFilter filter) {
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
        final Set<Method> methods = new HashSet<Method>();
        for (Location loc : locations) {
          if (acceptLocation(debugProcess, classType, loc)) {
            methods.add(loc.method());
          }
        }
        Location location = null;
        final int methodsFound = methods.size();
        if (methodsFound == 1) {
          location = methods.iterator().next().location();
        }
        else {
          if (myFilter instanceof LambdaMethodFilter) {
            final LambdaMethodFilter lambdaFilter = (LambdaMethodFilter)myFilter;
            if (lambdaFilter.getLambdaOrdinal() < methodsFound) {
              final Method[] candidates = methods.toArray(new Method[methodsFound]);
              Arrays.sort(candidates, DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR);
              location = candidates[lambdaFilter.getLambdaOrdinal()].location();
            }
          }
          else {
            if (methodsFound > 0) {
              location = methods.iterator().next().location();
            }
          }
        }
        if (location != null) {
          final RequestManagerImpl requestsManager = debugProcess.getRequestsManager();
          final BreakpointRequest request = requestsManager.createBreakpointRequest(this, location);
          requestsManager.enableRequest(request);
        }
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
    catch (InternalException ex) {
      LOG.info(ex);
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
}
