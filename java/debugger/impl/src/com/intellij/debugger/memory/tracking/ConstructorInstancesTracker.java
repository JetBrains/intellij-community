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
package com.intellij.debugger.memory.tracking;

import com.intellij.debugger.DebuggerManager;
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
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ConstructorInstancesTracker implements TrackerForNewInstances, Disposable, BackgroundTracker {
  private static final int TRACKED_INSTANCES_LIMIT = 2000;
  private final ReferenceType myReference;
  private final Project myProject;
  private final MyConstructorBreakpoints myBreakpoint;

  @Nullable
  private HashSet<ObjectReference> myNewObjects = null;

  @NotNull
  private HashSet<ObjectReference> myTrackedObjects = new HashSet<>();

  private volatile boolean myIsBackgroundMode;
  private volatile boolean myIsBackgroundTrackingEnabled;

  public ConstructorInstancesTracker(@NotNull ReferenceType ref,
                                     @NotNull XDebugSession debugSession) {
    myReference = ref;
    myProject = debugSession.getProject();
    myIsBackgroundTrackingEnabled = InstancesTracker.getInstance(myProject)
      .isBackgroundTrackingEnabled();

    final DebugProcessImpl debugProcess = (DebugProcessImpl)DebuggerManager.getInstance(myProject)
      .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

    InstancesTracker.getInstance(myProject).addTrackerListener(new InstancesTrackerListener() {
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

    final JavaLineBreakpointType breakPointType = new JavaLineBreakpointType();

    final XBreakpoint bpn = new XLineBreakpointImpl<>(breakPointType,
                                                      ((XDebuggerManagerImpl)XDebuggerManager.getInstance(myProject))
                                                        .getBreakpointManager(),
                                                      new JavaLineBreakpointProperties(),
                                                      new LineBreakpointState<>());

    myBreakpoint = new MyConstructorBreakpoints(myProject, bpn);
    myBreakpoint.createRequestForPreparedClass(debugProcess, myReference);
    Disposer.register(myBreakpoint, () -> debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        disable();
        debugProcess.getRequestsManager().deleteRequest(myBreakpoint);
        myBreakpoint.delete();
      }
    }));
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
    Disposer.dispose(myBreakpoint);
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

  private final class MyConstructorBreakpoints extends LineBreakpoint<JavaLineBreakpointProperties> implements Disposable {
    private final List<BreakpointRequest> myRequests = new ArrayList<>();
    private volatile boolean myIsEnabled = false;
    private volatile boolean myIsDeleted = false;

    MyConstructorBreakpoints(Project project, XBreakpoint xBreakpoint) {
      super(project, xBreakpoint);
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
    public void reload() {
    }

    void delete() {
      myIsDeleted = true;
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
      throws EventProcessingException {
      try {
        SuspendContextImpl suspendContext = action.getSuspendContext();
        if (suspendContext != null) {
          final MemoryViewDebugProcessData data = suspendContext.getDebugProcess().getUserData(MemoryViewDebugProcessData.KEY);
          ObjectReference thisRef = getThisObject(suspendContext, event);
          if (myReference.equals(thisRef.referenceType()) && data != null) {
            thisRef.disableCollection();
            myTrackedObjects.add(thisRef);
            final List<StackFrameItem> frame =
              StackFrameItem.createFrames(suspendContext.getThread(), suspendContext.getDebugProcess(), false);
            data.getTrackedStacks().addStack(thisRef, frame);
          }
        }
      }
      catch (EvaluateException e) {
        return false;
      }

      if (myTrackedObjects.size() >= TRACKED_INSTANCES_LIMIT) {
        disable();
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
  }
}
