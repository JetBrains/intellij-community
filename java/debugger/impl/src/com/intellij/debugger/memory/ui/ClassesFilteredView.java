// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.memory.ui;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.tracking.ConstructorInstancesTracker;
import com.intellij.debugger.memory.tracking.TrackerForNewInstances;
import com.intellij.debugger.memory.utils.AndroidUtil;
import com.intellij.debugger.memory.utils.LowestPriorityCommand;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.event.InstancesTrackerListener;
import com.intellij.xdebugger.memory.tracking.TrackingType;
import com.intellij.xdebugger.memory.ui.ClassesFilteredViewBase;
import com.intellij.xdebugger.memory.ui.ClassesTable;
import com.intellij.xdebugger.memory.ui.InstancesWindowBase;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.intellij.xdebugger.memory.ui.ClassesTable.DiffViewTableModel.CLASSNAME_COLUMN_INDEX;
import static com.intellij.xdebugger.memory.ui.ClassesTable.DiffViewTableModel.DIFF_COLUMN_INDEX;

public class ClassesFilteredView extends ClassesFilteredViewBase {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  public static final DataKey<InstancesProvider> NEW_INSTANCES_PROVIDER_KEY =
    DataKey.create("ClassesTable.NewInstances");

  private final InstancesTracker myInstancesTracker;

  /**
   * Indicates that the debug session had been stopped at least once.
   * <p>
   * State: false to true
   */
  private final AtomicBoolean myIsTrackersActivated = new AtomicBoolean(false);

  private final Map<ReferenceType, ConstructorInstancesTracker> myConstructorTrackedClasses = new ConcurrentHashMap<>();
  private final XDebugSessionListener additionalSessionListener;

  public ClassesFilteredView(@NotNull XDebugSession debugSession, @NotNull DebugProcessImpl debugProcess, @NotNull InstancesTracker tracker) {
    super(debugSession);
    final DebuggerManagerThreadImpl managerThread = debugProcess.getManagerThread();
    myInstancesTracker = tracker;
    final InstancesTrackerListener instancesTrackerListener = new InstancesTrackerListener() {
      @Override
      public void classChanged(@NotNull String name, @NotNull TrackingType type) {
        ClassesTable table = getTable();
        TypeInfo typeInfo = table.getClassByName(name);
        if (typeInfo == null)
          return;
        ReferenceType ref = ((JavaTypeInfo) typeInfo).getReferenceType();
        if (ref != null) {
          final boolean activated = myIsTrackersActivated.get();
          managerThread.schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() {
              trackClass(debugSession, ref, type, activated);
            }
          });
        }
        table.repaint();
      }

      @Override
      public void classRemoved(@NotNull String name) {
        ClassesTable table = getTable();
        TypeInfo ref = table.getClassByName(name);
        if (ref == null)
          return;
        JavaTypeInfo javaTypeInfo = (JavaTypeInfo) ref;
        if (myConstructorTrackedClasses.containsKey(javaTypeInfo.getReferenceType())) {
          ConstructorInstancesTracker removed = myConstructorTrackedClasses.remove(javaTypeInfo.getReferenceType());
          Disposer.dispose(removed);
          table.getRowSorter().allRowsChanged();
        }
      }
    };
    debugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        myInstancesTracker.removeTrackerListener(instancesTrackerListener);
      }
    });
    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processAttached(DebugProcess process) {
        debugProcess.removeDebugProcessListener(this);
        managerThread.invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            final boolean activated = myIsTrackersActivated.get();
            final VirtualMachineProxyImpl proxy = debugProcess.getVirtualMachineProxy();
            tracker.getTrackedClasses().forEach((className, type) -> {
              List<ReferenceType> classes = proxy.classesByName(className);
              if (classes.isEmpty()) {
                trackWhenPrepared(className, debugSession, debugProcess, type);
              }
              else {
                for (ReferenceType ref : classes) {
                  trackClass(debugSession, ref, type, activated);
                }
              }
            });

            tracker.addTrackerListener(instancesTrackerListener);
          }
        });
      }

      private void trackWhenPrepared(@NotNull String className,
                                     @NotNull XDebugSession session,
                                     @NotNull DebugProcessImpl process,
                                     @NotNull TrackingType type) {
        final ClassPrepareRequestor request = new ClassPrepareRequestor() {
          @Override
          public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
            process.getRequestsManager().deleteRequest(this);
            trackClass(session, referenceType, type, myIsTrackersActivated.get());
          }
        };

        final ClassPrepareRequest classPrepareRequest = process.getRequestsManager()
                                                               .createClassPrepareRequest(request, className);
        if (classPrepareRequest != null) {
          classPrepareRequest.enable();
        }
        else {
          LOG.warn("Cannot create a 'class prepare' request. Class " + className + " not tracked.");
        }
      }
    });
    additionalSessionListener = new XDebugSessionListener() {
      @Override
      public void sessionResumed() {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::obsolete);
      }

      @Override
      public void sessionStopped() {
        myConstructorTrackedClasses.values().forEach(Disposer::dispose);
        myConstructorTrackedClasses.clear();
      }
    };
    ClassesTable table = getTable();
    table.addMouseMotionListener(new MyMouseMotionListener());
    table.addMouseListener(new MyOpenNewInstancesListener());
    new MyDoubleClickListener().installOn(table);
  }
  private void trackClass(@NotNull XDebugSession session,
                          @NotNull ReferenceType ref,
                          @NotNull TrackingType type,
                          boolean isTrackerEnabled) {
    LOG.assertTrue(DebuggerManager.getInstance(myProject).isDebuggerManagerThread());
    if (type == TrackingType.CREATION) {
      final ConstructorInstancesTracker old = myConstructorTrackedClasses.getOrDefault(ref, null);
      if (old != null) {
        Disposer.dispose(old);
      }

      final ConstructorInstancesTracker tracker = new ConstructorInstancesTracker(ref, session, myInstancesTracker);
      tracker.setBackgroundMode(!myIsActive);
      if (isTrackerEnabled) {
        tracker.enable();
      }
      else {
        tracker.disable();
      }

      myConstructorTrackedClasses.put(ref, tracker);
    }
  }

  @Override
  protected void scheduleUpdateClassesCommand(XSuspendContext context) {
    SuspendContextImpl suspendContext = (SuspendContextImpl) context;
    suspendContext.getDebugProcess().getManagerThread().schedule(new MyUpdateClassesCommand(suspendContext));
  }

  @Nullable
  @Override
  protected TrackerForNewInstances getStrategy(@NotNull TypeInfo ref) {
    JavaTypeInfo javaTypeInfo = (JavaTypeInfo) ref;
    return myConstructorTrackedClasses.getOrDefault(javaTypeInfo.getReferenceType(), null);
  }

  @Override
  protected InstancesWindowBase getInstancesWindow(@NotNull TypeInfo ref, XDebugSession debugSession) {
    return new InstancesWindow(debugSession, limit -> ref.getInstances(limit), ref.name());
  }

  @Override
  protected void doActivate() {
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(false));
    super.doActivate();
  }

  @Override
  protected void doPause() {
    super.doPause();
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(true));
  }

  @Override
  public void dispose() {
    myConstructorTrackedClasses.clear();
  }

  @Override
  public Object getData(String dataId) {
    if (NEW_INSTANCES_PROVIDER_KEY.is(dataId)) {
      TypeInfo selectedClass = getTable().getSelectedClass();
      if (selectedClass != null) {
        TrackerForNewInstances strategy = getStrategy(selectedClass);
        if (strategy != null && strategy.isReady()) {
          List<ObjectReference> newInstances = strategy.getNewInstances();
          return (InstancesProvider) limit -> newInstances.stream().map(JavaReferenceInfo::new).collect(Collectors.toList());
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  protected XDebugSessionListener getAdditionalSessionListener() {
    return additionalSessionListener;
  }

  public void setActive(boolean active, @NotNull DebuggerManagerThreadImpl managerThread) {
    if (myIsActive == active) {
      return;
    }

    myIsActive = active;

    managerThread.schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        if (active) {
          doActivate();
        }
        else {
          doPause();
        }
      }
    });
  }

  private void commitAllTrackers() {
    myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::commitTracked);
  }

  private boolean isShowNewInstancesEvent(@NotNull MouseEvent e) {
    ClassesTable table = getTable();
    final int col = table.columnAtPoint(e.getPoint());
    final int row = table.rowAtPoint(e.getPoint());
    if (col == -1 || row == -1 || table.convertColumnIndexToModel(col) != DIFF_COLUMN_INDEX) {
      return false;
    }

    final int modelRow = table.convertRowIndexToModel(row);

    final JavaTypeInfo ref = (JavaTypeInfo) table.getModel().getValueAt(modelRow, CLASSNAME_COLUMN_INDEX);
    final ConstructorInstancesTracker tracker = myConstructorTrackedClasses.getOrDefault(ref.getReferenceType(), null);

    return tracker != null && tracker.isReady() && tracker.getCount() > 0;
  }

  private class MyOpenNewInstancesListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() != 1 || e.getButton() != MouseEvent.BUTTON1 || !isShowNewInstancesEvent(e)) {
        return;
      }

      TypeInfo selectedTypeInfo = getTable().getSelectedClass();
      final ReferenceType ref = selectedTypeInfo != null ? ((JavaTypeInfo) selectedTypeInfo).getReferenceType(): null;
      final TrackerForNewInstances strategy = ref == null ? null : getStrategy(selectedTypeInfo);
      XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
      if (strategy != null && debugSession != null) {
        final DebugProcess debugProcess =
          DebuggerManager.getInstance(myProject).getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
        final MemoryViewDebugProcessData data = debugProcess.getUserData(MemoryViewDebugProcessData.KEY);
        if (data != null) {
          final List<ObjectReference> newInstances = strategy.getNewInstances();
          data.getTrackedStacks().pinStacks(ref);
          final InstancesWindow instancesWindow = new InstancesWindow(debugSession, limit -> newInstances.stream().map(JavaReferenceInfo::new).collect(Collectors.toList()), ref.name());
          Disposer.register(instancesWindow.getDisposable(), () -> data.getTrackedStacks().unpinStacks(ref));
          instancesWindow.show();
        }
        else {
          LOG.warn("MemoryViewDebugProcessData not found in debug session user data");
        }
      }
    }
  }

  private class MyMouseMotionListener implements MouseMotionListener {
    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      ClassesTable table = getTable();
      if (table.isInClickableMode()) return;

      if (isShowNewInstancesEvent(e)) {
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        table.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
  }

  private class MyDoubleClickListener extends DoubleClickListener {
    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      if (!isShowNewInstancesEvent(event)) {
        handleClassSelection(getTable().getSelectedClass());
        return true;
      }

      return false;
    }
  }

  private final class MyUpdateClassesCommand extends LowestPriorityCommand {

    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) {
      handleTrackers();

      final VirtualMachineProxyImpl proxy = suspendContext.getDebugProcess().getVirtualMachineProxy();
      final List<ReferenceType> classes = proxy.allClasses();

      ClassesTable table = getTable();

      if (!classes.isEmpty()) {
        final VirtualMachine vm = classes.get(0).virtualMachine();
        if (vm.canGetInstanceInfo()) {
          final Map<TypeInfo, Long> counts = getInstancesCounts(classes, vm);
          ApplicationManager.getApplication().invokeLater(() -> table.updateContent(counts));
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> table.updateClassesOnly(JavaTypeInfo.wrap(classes)));
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> table.setBusy(false));
      viewUpdated();
    }

    private void handleTrackers() {
      if (!myIsTrackersActivated.get()) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::enable);
        myIsTrackersActivated.set(true);
      }
      else {
        commitAllTrackers();
      }
    }

    private Map<TypeInfo, Long> getInstancesCounts(@NotNull List<ReferenceType> classes, @NotNull VirtualMachine vm) {
      final int batchSize = DebuggerUtils.isAndroidVM(vm)
                            ? AndroidUtil.ANDROID_COUNT_BY_CLASSES_BATCH_SIZE
                            : DEFAULT_BATCH_SIZE;

      final int size = classes.size();
      final Map<TypeInfo, Long> result = new LinkedHashMap<>();

      for (int begin = 0, end = Math.min(batchSize, size);
           begin != size;
           begin = end, end = Math.min(end + batchSize, size)) {
        final List<ReferenceType> batch = classes.subList(begin, end);

        final long start = System.nanoTime();
        final long[] counts = vm.instanceCounts(batch);
        final long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        for (int i = 0; i < batch.size(); i++) {
          result.put(new JavaTypeInfo(batch.get(i)), counts[i]);
        }

        final int waitTime = (int)Math.min(DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay, MAX_DELAY_MILLIS);
        mySingleAlarm.setDelay(waitTime);
        LOG.debug(String.format("Instances query time = %d ms. Count of classes = %d", delay, batch.size()));
      }

      return result;
    }
  }
}
