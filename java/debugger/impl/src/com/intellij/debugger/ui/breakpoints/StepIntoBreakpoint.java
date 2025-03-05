// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class StepIntoBreakpoint extends RunToCursorBreakpoint {
  private static final Logger LOG = Logger.getInstance(StepIntoBreakpoint.class);
  private final @NotNull BreakpointStepMethodFilter myFilter;
  private @Nullable RequestHint myHint;

  protected StepIntoBreakpoint(@NotNull Project project, @NotNull SourcePosition pos, @NotNull BreakpointStepMethodFilter filter) {
    super(project, pos, false);
    myFilter = filter;
  }

  @Override
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
        List<Location> acceptedLocations = new ArrayList<>();
        final int methodsFound = methods.size();
        if (methodsFound > 1 && myFilter instanceof LambdaMethodFilter lambdaFilter) {
          if (lambdaFilter.getLambdaOrdinal() < methodsFound) {
            Method[] candidates = methods.keySet().toArray(new Method[methodsFound]);
            Arrays.sort(candidates, DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR);
            acceptedLocations.addAll(methods.get(candidates[lambdaFilter.getLambdaOrdinal()]));
          }
        }
        else {
          acceptedLocations.addAll(methods.values());
        }
        for (Location location : acceptedLocations) {
          createLocationBreakpointRequest(this, location, debugProcess);
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
    catch (Exception ex) {
      LOG.info(ex);
    }
  }

  @Override
  protected boolean acceptLocation(DebugProcessImpl debugProcess, ReferenceType classType, Location loc) {
    try {
      return myFilter.locationMatches(debugProcess, loc);
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    return true;
  }

  protected static @Nullable StepIntoBreakpoint create(@NotNull Project project, @NotNull BreakpointStepMethodFilter filter) {
    final SourcePosition pos = filter.getBreakpointPosition();
    if (pos != null) {
      final StepIntoBreakpoint breakpoint = new StepIntoBreakpoint(project, pos, filter);
      breakpoint.init();
      return breakpoint;
    }
    return null;
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    boolean res = super.processLocatableEvent(action, event);
    SuspendContextImpl context = action.getSuspendContext();
    if (res && context != null) {
      context.getDebugProcess().resetIgnoreSteppingFilters(event.location(), myHint);
    }
    return res;
  }

  @Override
  public void setRequestHint(RequestHint hint) {
    myHint = hint;
  }
}
