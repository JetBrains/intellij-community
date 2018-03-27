/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.memory.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.component.InstancesTracker;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.event.InstancesTrackerListener;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.SyntheticLineBreakpoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ConstructorInstancesTracker implements TrackerForNewInstances, Disposable, BackgroundTracker {
  private static final int TRACKED_INSTANCES_LIMIT = 2000;
  private final String myClassName;
  private final Project myProject;
  private final MyConstructorBreakpoints myBreakpoint;

  @Nullable
  private HashSet<ObjectReference> myNewObjects = null;

  @NotNull
  private HashSet<ObjectReference> myTrackedObjects = new HashSet<>();

  private volatile boolean myIsBackgroundMode;
  private volatile boolean myIsBackgroundTrackingEnabled;

  public ConstructorInstancesTracker(@NotNull ReferenceType ref,
                                     @NotNull XDebugSession debugSession,
                                     @NotNull InstancesTracker instancesTracker) {
    myProject = debugSession.getProject();
    myIsBackgroundTrackingEnabled = instancesTracker.isBackgroundTrackingEnabled();

    myClassName = ref.name();
    final DebugProcessImpl debugProcess = (DebugProcessImpl)DebuggerManager.getInstance(myProject)
      .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

    instancesTracker.addTrackerListener(new InstancesTrackerListener() {
      @Override
      public void backgroundTrackingValueChanged(boolean newState) {
        if (myIsBackgroundTrackingEnabled != newState) {
          myIsBackgroundTrackingEnabled = newState;
          debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
              if (newState) {
                myBreakpoint.enable();
              }
              else {
                myBreakpoint.disable();
              }
            }
          });
        }
      }
    }, this);

    myBreakpoint = new MyConstructorBreakpoints(myProject);
    myBreakpoint.createRequestForPreparedClass(debugProcess, ref);
  }

  public void obsolete() {
    if (myNewObjects != null) {
      myNewObjects.forEach(ObjectReference::enableCollection);
    }

    myNewObjects = null;
    if (!myIsBackgroundMode || myIsBackgroundTrackingEnabled) {
      myBreakpoint.enable();
    }

    final XDebugSession session = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (session != null) {
      final DebugProcess process = DebuggerManager.getInstance(myProject).getDebugProcess(session.getDebugProcess().getProcessHandler());
      final MemoryViewDebugProcessData data = process.getUserData(MemoryViewDebugProcessData.KEY);
      if (data != null) {
        data.getTrackedStacks().release();
      }
    }
  }

  public void commitTracked() {
    myNewObjects = myTrackedObjects;
    myTrackedObjects = new HashSet<>();
  }

  @NotNull
  @Override
  public List<ObjectReference> getNewInstances() {
    return myNewObjects == null ? Collections.EMPTY_LIST : new ArrayList<>(myNewObjects);
  }

  @Override
  public int getCount() {
    return myNewObjects == null ? 0 : myNewObjects.size();
  }

  public void enable() {
    myBreakpoint.enable();
  }

  public void disable() {
    myBreakpoint.disable();
  }

  @Override
  public boolean isReady() {
    return myNewObjects != null;
  }

  @Override
  public void dispose() {
    myBreakpoint.delete();
    myTrackedObjects.clear();
    myNewObjects = null;
  }

  @Override
  public void setBackgroundMode(boolean isBackgroundMode) {
    if (myIsBackgroundMode == isBackgroundMode) {
      return;
    }

    myIsBackgroundMode = isBackgroundMode;
    if (isBackgroundMode) {
      doEnableBackgroundMode();
    }
    else {
      doDisableBackgroundMode();
    }
  }

  private void doDisableBackgroundMode() {
    myBreakpoint.enable();
  }

  private void doEnableBackgroundMode() {
    if (!myIsBackgroundTrackingEnabled) {
      myBreakpoint.disable();
    }
  }

  private final class MyConstructorBreakpoints extends MyConstructorBreakpointBase {
    private final List<BreakpointRequest> myRequests = new ArrayList<>();
    private final String myDisplayName = "MemoryViewConstructorTracker:" + myClassName;
    private volatile boolean myIsEnabled = false;
    private volatile boolean myIsDeleted = false;

    MyConstructorBreakpoints(Project project) {
      super(project);
      setVisible(false);
    }

    @Override
    protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
      classType.methods().stream().filter(Method::isConstructor).forEach(cons -> {
        Location loc = cons.location();
        BreakpointRequest breakpointRequest = debugProcess.getRequestsManager().createBreakpointRequest(this, loc);
        myRequests.add(breakpointRequest);
      });

      if (!myIsBackgroundMode || myIsBackgroundTrackingEnabled) {
        enable();
      }
    }

    @Override
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public boolean isEnabled() {
      return myIsEnabled;
    }

    void delete() {
      // unable disable all requests here because VMDisconnectedException can be thrown
      myRequests.clear();
      myIsDeleted = true;
    }

    @Override
    public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
      throws EventProcessingException {
      if (myIsDeleted) {
        event.request().disable();
      }
      else {
        handleEvent(action, event);
      }

      return false;
    }

    void enable() {
      if (!myIsEnabled && !myIsDeleted) {
        myRequests.forEach(EventRequest::enable);
        myIsEnabled = true;
      }
    }

    void disable() {
      if (myIsEnabled && !myIsDeleted) {
        myRequests.forEach(EventRequest::disable);
        myIsEnabled = false;
      }
    }

    private void handleEvent(@NotNull SuspendContextCommandImpl action, @NotNull LocatableEvent event) {
      try {
        SuspendContextImpl suspendContext = action.getSuspendContext();
        if (suspendContext != null) {
          final MemoryViewDebugProcessData data = suspendContext.getDebugProcess().getUserData(MemoryViewDebugProcessData.KEY);
          ObjectReference thisRef = getThisObject(suspendContext, event);
          if (thisRef != null && thisRef.referenceType().name().equals(myClassName) && data != null) {
            thisRef.disableCollection();
            myTrackedObjects.add(thisRef);
            data.getTrackedStacks().addStack(thisRef, StackFrameItem.createFrames(suspendContext, false));
          }
        }
      }
      catch (EvaluateException ignored) {
      }

      if (myTrackedObjects.size() >= TRACKED_INSTANCES_LIMIT) {
        disable();
      }
    }
  }

  /**
   * Contains stubs for all methods which can use xBreakpoint implicitly
   * Inspired by com.intellij.debugger.ui.breakpoints.RunToCursorBreakpoint
   */
  private static class MyConstructorBreakpointBase extends SyntheticLineBreakpoint {
    protected MyConstructorBreakpointBase(@NotNull Project project) {
      super(project);
      setSuspendPolicy(DebuggerSettings.SUSPEND_THREAD);
    }

    @Nullable
    @Override
    public SourcePosition getSourcePosition() {
      return null;
    }

    @Override
    public int getLineIndex() {
      return -1;
    }

    @Override
    public String getEventMessage(LocatableEvent event) {
      return "";
    }

    @Nullable
    @Override
    protected JavaLineBreakpointType getXBreakpointType() {
      return null;
    }
  }
}
