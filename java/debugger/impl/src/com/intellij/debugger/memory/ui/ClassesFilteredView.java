/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.component.*;
import com.intellij.debugger.memory.event.InstancesTrackerListener;
import com.intellij.debugger.memory.event.MemoryViewManagerListener;
import com.intellij.debugger.memory.tracking.ClassPreparedListener;
import com.intellij.debugger.memory.tracking.ConstructorInstancesTracker;
import com.intellij.debugger.memory.tracking.TrackerForNewInstances;
import com.intellij.debugger.memory.tracking.TrackingType;
import com.intellij.debugger.memory.utils.AndroidUtil;
import com.intellij.debugger.memory.utils.KeyboardUtils;
import com.intellij.debugger.memory.utils.LowestPriorityCommand;
import com.intellij.debugger.memory.utils.SingleAlarmWithMutableDelay;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.intellij.debugger.memory.ui.ClassesTable.DiffViewTableModel.CLASSNAME_COLUMN_INDEX;
import static com.intellij.debugger.memory.ui.ClassesTable.DiffViewTableModel.DIFF_COLUMN_INDEX;

public class ClassesFilteredView extends BorderLayoutPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private final static double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  private final static double MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);
  private final static int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final String EMPTY_TABLE_CONTENT_WHEN_RUNNING = "The application is running";
  private static final String EMPTY_TABLE_CONTENT_WHEN_SUSPENDED = "Nothing to show";

  private final Project myProject;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;
  private final InstancesTracker myInstancesTracker;
  private final Map<ReferenceType, ConstructorInstancesTracker> myConstructorTrackedClasses = new ConcurrentHashMap<>();
  private final MyDebuggerSessionListener myDebugSessionListener;

  @Nullable
  private volatile SuspendContextImpl myLastSuspendContext;

  /**
   * Indicates that the debug session had been stopped at least once.
   * <p>
   * State: false to true
   */
  private final AtomicBoolean myIsTrackersActivated = new AtomicBoolean(false);

  /**
   * Indicates that view is visible in tool window
   */
  private boolean myIsActive;

  public ClassesFilteredView(@NotNull XDebugSession debugSession) {
    super();

    myProject = debugSession.getProject();
    final DebugProcessImpl debugProcess = (DebugProcessImpl)DebuggerManager.getInstance(myProject)
      .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

    if (debugProcess == null) {
      throw new NullPointerException("Failed to receive a java debug process");
    }

    myInstancesTracker = InstancesTracker.getInstance(myProject);
    final InstancesTrackerListener instancesTrackerListener = new InstancesTrackerListener() {
      @Override
      public void classChanged(@NotNull String name, @NotNull TrackingType type) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null) {
          final boolean activated = myIsTrackersActivated.get();
          debugProcess.getManagerThread()
            .schedule(new LowestPriorityCommand(getSuspendContext()) {
              @Override
              public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
                trackClass(ref, type, activated);
              }
            });
        }
        myTable.repaint();
      }

      @Override
      public void classRemoved(@NotNull String name) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null && myConstructorTrackedClasses.containsKey(ref)) {
          ConstructorInstancesTracker removed = myConstructorTrackedClasses.remove(ref);
          Disposer.dispose(removed);
          myTable.getRowSorter().allRowsChanged();
        }
      }
    };

    debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      public Priority getPriority() {
        return Priority.LOWEST;
      }

      @Override
      protected void action() throws Exception {
        final boolean activated = myIsTrackersActivated.get();
        for (Map.Entry<String, TrackingType> entry : myInstancesTracker.getTrackedClasses().entrySet()) {
          TrackingType type = entry.getValue();
          String className = entry.getKey();
          List<ReferenceType> classes = debugProcess.getVirtualMachineProxy().classesByName(className);
          if (classes.isEmpty()) {
            new ClassPreparedListener(className, debugSession) {
              @Override
              public void onClassPrepared(@NotNull ReferenceType referenceType) {
                trackClass(referenceType, type, myIsTrackersActivated.get());
              }
            };
          }
          else {
            for (ReferenceType ref : classes) {
              trackClass(ref, type, activated);
            }
          }
        }

        myInstancesTracker.addTrackerListener(instancesTrackerListener, ClassesFilteredView.this);
      }
    });

    final MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = new ClassesTable(debugSession, memoryViewManagerState.isShowWithDiffOnly,
                               memoryViewManagerState.isShowWithInstancesOnly, memoryViewManagerState.isShowTrackedOnly, this);
    myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
    Disposer.register(this, myTable);

    myTable.addMouseMotionListener(new MyMouseMotionListener());
    myTable.addMouseListener(new MyOpenNewInstancesListener());
    new MyDoubleClickListener().installOn(myTable);

    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (KeyboardUtils.isEnterKey(keyCode)) {
          handleClassSelection(myTable.getSelectedClass());
        }
        else if (KeyboardUtils.isCharacter(keyCode) || KeyboardUtils.isBackSpace(keyCode)) {
          final String text = myFilterTextField.getText();
          final String newText = KeyboardUtils.isBackSpace(keyCode)
                                 ? text.substring(0, text.length() - 1)
                                 : text + e.getKeyChar();
          myFilterTextField.setText(newText);
          FocusManager.getCurrentManager().focusNextComponent(myFilterTextField);
        }
      }
    });

    myFilterTextField.addKeyboardListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        dispatch(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        dispatch(e);
      }

      private void dispatch(KeyEvent e) {
        if (KeyboardUtils.isUpDownKey(e.getKeyCode()) || KeyboardUtils.isEnterKey(e.getKeyCode())) {
          myTable.dispatchEvent(e);
        }
      }
    });

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
      }
    });

    final MemoryViewManagerListener memoryViewManagerListener = state -> {
      myTable.setFilteringByDiffNonZero(state.isShowWithDiffOnly);
      myTable.setFilteringByInstanceExists(state.isShowWithInstancesOnly);
      myTable.setFilteringByTrackingState(state.isShowTrackedOnly);
    };

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener, this);

    myDebugSessionListener = new MyDebuggerSessionListener();
    debugSession.addSessionListener(myDebugSessionListener, this);

    mySingleAlarm = new SingleAlarmWithMutableDelay(() -> {
      myLastSuspendContext = getSuspendContext();
      if (myLastSuspendContext != null) {
        ApplicationManager.getApplication().invokeLater(() -> myTable.setBusy(true));
        debugProcess.getManagerThread()
          .schedule(new MyUpdateClassesCommand(myLastSuspendContext));
      }
    }, this);

    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu menu = createContextMenu();
        if (menu != null) {
          menu.getComponent().show(comp, x, y);
        }
      }
    });

    final JScrollPane scroll = ScrollPaneFactory.createScrollPane(myTable, SideBorder.TOP);
    addToTop(myFilterTextField);
    addToCenter(scroll);
  }

  @Nullable
  TrackerForNewInstances getStrategy(@NotNull ReferenceType ref) {
    return myConstructorTrackedClasses.getOrDefault(ref, null);
  }

  private void trackClass(@NotNull ReferenceType ref,
                          @NotNull TrackingType type,
                          boolean isTrackerEnabled) {
    LOG.assertTrue(DebuggerManager.getInstance(myProject).isDebuggerManagerThread());
    if (type == TrackingType.CREATION) {
      final ConstructorInstancesTracker old = myConstructorTrackedClasses.getOrDefault(ref, null);
      if (old != null) {
        Disposer.dispose(old);
      }

      final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
      if (debugSession == null) {
        return;
      }

      final ConstructorInstancesTracker tracker = new ConstructorInstancesTracker(ref, debugSession);
      tracker.setBackgroundMode(!myIsActive);
      if (isTrackerEnabled) {
        tracker.enable();
      }
      else {
        tracker.disable();
      }

      Disposer.register(this, tracker);
      myConstructorTrackedClasses.put(ref, tracker);
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (ref != null && debugSession != null && debugSession.isSuspended()) {
      new InstancesWindow(debugSession, limit -> ref.instances(limit), ref.name()).show();
    }
  }

  private void commitAllTrackers() {
    myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::commitTracked);
  }

  private SuspendContextImpl getSuspendContext() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContext().getSuspendContext();
  }

  private void updateClassesAndCounts() {
    final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (debugSession != null) {
      final DebugProcess debugProcess = DebuggerManager.getInstance(myProject)
        .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
      if (debugProcess.isAttached()) {
        mySingleAlarm.cancelAndRequest();
      }
    }
  }

  private static ActionPopupMenu createContextMenu() {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MemoryView.ClassesPopupActionGroup");
    return ActionManager.getInstance().createActionPopupMenu("MemoryView.ClassesPopupActionGroup", group);
  }

  @Override
  public void dispose() {
    myLastSuspendContext = null;
    myConstructorTrackedClasses.clear();
  }

  public void setActive(boolean active) {
    if (myIsActive == active) {
      return;
    }

    myIsActive = active;
    final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (debugSession != null) {
      final DebugProcessImpl debugProcess =
        (DebugProcessImpl)DebuggerManager.getInstance(myProject).getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
      debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          if (active) {
            doActivate();
          }
          else {
            doPause();
          }
        }
      });
    }
  }

  private void doActivate() {
    myDebugSessionListener.setActive(true);
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(false));
    final SuspendContextImpl lastContext = myLastSuspendContext;

    if (lastContext == null || !lastContext.equals(getSuspendContext())) {
      updateClassesAndCounts();
    }
  }

  private void doPause() {
    myDebugSessionListener.setActive(false);
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(true));
  }

  private final class MyUpdateClassesCommand extends LowestPriorityCommand {

    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
      if (!myIsTrackersActivated.get()) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::enable);
        myIsTrackersActivated.set(true);
      }
      else {
        commitAllTrackers();
      }

      final List<ReferenceType> classes = suspendContext.getDebugProcess().getVirtualMachineProxy().allClasses();

      if (classes.isEmpty()) {
        return;
      }

      final VirtualMachine vm = classes.get(0).virtualMachine();
      final int batchSize = AndroidUtil.isAndroidVM(vm)
                            ? AndroidUtil.ANDROID_COUNT_BY_CLASSES_BATCH_SIZE
                            : DEFAULT_BATCH_SIZE;

      final List<long[]> chunks = new SmartList<>();
      final int size = classes.size();
      for (int begin = 0, end = Math.min(batchSize, size);
           begin != size && isContextValid();
           begin = end, end = Math.min(end + batchSize, size)) {
        final List<ReferenceType> batch = classes.subList(begin, end);

        final long start = System.nanoTime();
        final long[] counts = vm.instanceCounts(batch);
        final long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        chunks.add(counts);

        final int waitTime = (int)Math.min(DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay, MAX_DELAY_MILLIS);
        mySingleAlarm.setDelay(waitTime);
        LOG.info(String.format("Instances query time = %d ms. Count = %d", delay, batch.size()));
      }

      if (isContextValid()) {
        final long[] counts = chunks.size() == 1 ? chunks.get(0) : IntStream.range(0, chunks.size()).boxed()
          .flatMapToLong(integer -> Arrays.stream(chunks.get(integer)))
          .toArray();
        ApplicationManager.getApplication().invokeLater(() -> myTable.setClassesAndUpdateCounts(classes, counts));
      }

      ApplicationManager.getApplication().invokeLater(() -> myTable.setBusy(false));
    }

    private boolean isContextValid() {
      return ClassesFilteredView.this.getSuspendContext() == getSuspendContext();
    }
  }

  private static class FilterTextField extends SearchTextField {
    FilterTextField() {
      super(false);
    }

    @Override
    protected void showPopup() {
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
    }
  }

  private class MyOpenNewInstancesListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() != 1 || e.getButton() != MouseEvent.BUTTON1 || !isShowNewInstancesEvent(e)) {
        return;
      }

      final ReferenceType ref = myTable.getSelectedClass();
      final TrackerForNewInstances strategy = ref == null ? null : getStrategy(ref);
      XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
      if (strategy != null && debugSession != null) {
        final DebugProcess debugProcess =
          DebuggerManager.getInstance(myProject).getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
        final MemoryViewDebugProcessData data = debugProcess.getUserData(MemoryViewDebugProcessData.KEY);
        if (data != null) {
          final List<ObjectReference> newInstances = strategy.getNewInstances();
          data.getTrackedStacks().pinStacks(ref);
          final InstancesWindow instancesWindow = new InstancesWindow(debugSession, limit -> newInstances, ref.name());
          Disposer.register(instancesWindow.getDisposable(), () -> data.getTrackedStacks().unpinStacks(ref));
          instancesWindow.show();
        }
        else {
          LOG.warn("MemoryViewDebugProcessData not found in debug session user data");
        }
      }
    }
  }

  private class MyDoubleClickListener extends DoubleClickListener {
    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      if (!isShowNewInstancesEvent(event)) {
        handleClassSelection(myTable.getSelectedClass());
        return true;
      }

      return false;
    }
  }

  private class MyMouseMotionListener implements MouseMotionListener {
    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isShowNewInstancesEvent(e)) {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
  }

  private boolean isShowNewInstancesEvent(@NotNull MouseEvent e) {
    final int col = myTable.columnAtPoint(e.getPoint());
    final int row = myTable.rowAtPoint(e.getPoint());
    if (col == -1 || row == -1 || myTable.convertColumnIndexToModel(col) != DIFF_COLUMN_INDEX) {
      return false;
    }

    final int modelRow = myTable.convertRowIndexToModel(row);

    final ReferenceType ref = (ReferenceType)myTable.getModel().getValueAt(modelRow, CLASSNAME_COLUMN_INDEX);
    final ConstructorInstancesTracker tracker = myConstructorTrackedClasses.getOrDefault(ref, null);

    return tracker != null && tracker.isReady() && tracker.getCount() > 0;
  }

  private class MyDebuggerSessionListener implements XDebugSessionListener {
    private volatile boolean myIsActive = false;

    void setActive(boolean value) {
      myIsActive = value;
    }

    @Override
    public void sessionResumed() {
      if (myIsActive) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::obsolete);
        ApplicationManager.getApplication().invokeLater(() -> {
          myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
          myTable.hideContent();
        });
        mySingleAlarm.cancelAllRequests();
      }
    }

    @Override
    public void sessionPaused() {
      if (myIsActive) {
        ApplicationManager.getApplication().invokeLater(() -> myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_SUSPENDED));
        updateClassesAndCounts();
      }
    }
  }
}
