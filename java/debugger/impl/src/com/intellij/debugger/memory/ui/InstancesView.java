// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.memory.agent.MemoryAgentUtil;
import com.intellij.debugger.memory.filtering.FilteringResult;
import com.intellij.debugger.memory.filtering.FilteringTask;
import com.intellij.debugger.memory.filtering.FilteringTaskCallback;
import com.intellij.debugger.memory.utils.AndroidUtil;
import com.intellij.debugger.memory.utils.ErrorsValueGroup;
import com.intellij.debugger.memory.utils.InstanceJavaValue;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import com.intellij.xdebugger.memory.ui.InstancesTree;
import com.intellij.xdebugger.memory.ui.InstancesViewBase;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class InstancesView extends InstancesViewBase {
  private static final Logger LOG = Logger.getInstance(InstancesView.class);
  private static final int MAX_TREE_NODE_COUNT = 2000;
  private static final int FILTERING_CHUNK_SIZE = 50;
  private static final int FILTERING_BUTTON_ADDITIONAL_WIDTH = 30;
  private static final int BORDER_LAYOUT_DEFAULT_GAP = 5;
  private static final int DEFAULT_INSTANCES_LIMIT = 500000;

  private static final int MAX_DURATION_TO_UPDATE_TREE_SECONDS = 3;
  private static final int FILTERING_PROGRESS_UPDATING_MIN_DELAY_MILLIS = 17; // ~ 60 fps

  private final InstancesTree myInstancesTree;
  private final XDebuggerExpressionEditor myFilterConditionEditor;

  private final MyNodeManager myNodeManager;
  private final Consumer<? super String> myWarningMessageConsumer;

  private final JButton myFilterButton = new JButton(CommonBundle.message("button.filter"));
  private final FilteringProgressView myProgress = new FilteringProgressView();

  private final Object myFilteringTaskLock = new Object();

  private boolean myIsAndroidVM;
  private final DebugProcessImpl myDebugProcess;
  private final String myClassName;


  private volatile FilteringTask myFilteringTask;
  private volatile Future<?> myFilteringTaskFuture;

  InstancesView(@NotNull XDebugSession session, InstancesProvider instancesProvider, String className, Consumer<? super String> warningMessageConsumer) {
    super(new BorderLayout(0, JBUIScale.scale(BORDER_LAYOUT_DEFAULT_GAP)), session, instancesProvider);
    myClassName = className;
    myDebugProcess = (DebugProcessImpl)(DebuggerManager.getInstance(session.getProject()).getDebugProcess(session.getDebugProcess().getProcessHandler()));
    myNodeManager = new MyNodeManager(session.getProject());
    myWarningMessageConsumer = warningMessageConsumer;

    final XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();

    myFilterConditionEditor = new ExpressionEditorWithHistory(session.getProject(), className,
                                                              editorsProvider, this);

    final Dimension filteringButtonSize = myFilterConditionEditor.getEditorComponent().getPreferredSize();
    filteringButtonSize.width = JBUIScale.scale(FILTERING_BUTTON_ADDITIONAL_WIDTH) +
                                getFilterButton().getPreferredSize().width;
    getFilterButton().setPreferredSize(filteringButtonSize);

    final JBPanel filteringPane = new JBPanel(new BorderLayout(JBUIScale.scale(BORDER_LAYOUT_DEFAULT_GAP), 0));
    final JBLabel sideEffectsWarning = new JBLabel(JavaDebuggerBundle.message("warning.filtering.may.have.side.effects"), SwingConstants.RIGHT);
    sideEffectsWarning.setBorder(JBUI.Borders.emptyTop(1));
    sideEffectsWarning.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    sideEffectsWarning.setFontColor(UIUtil.FontColor.BRIGHTER);

    filteringPane.add(new JBLabel(JavaDebuggerBundle.message("condition")), BorderLayout.WEST);
    filteringPane.add(myFilterConditionEditor.getComponent(), BorderLayout.CENTER);
    filteringPane.add(getFilterButton(), BorderLayout.EAST);
    filteringPane.add(sideEffectsWarning, BorderLayout.SOUTH);

    getProgress().addStopActionListener(this::cancelFilteringTask);

    myInstancesTree = new InstancesTree(session.getProject(), editorsProvider, getValueMarkers(session), this::updateInstances);

    getFilterButton().addActionListener(e -> {
      final String expression = myFilterConditionEditor.getExpression().getExpression();
      if (!expression.isEmpty()) {
        myFilterConditionEditor.saveTextInHistory();
      }

      getFilterButton().setEnabled(false);
      myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES);
    });


    final StackFrameList list = new StackFrameList(myDebugProcess);

    list.addListSelectionListener(e -> list.navigateToSelectedValue(false));
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        list.navigateToSelectedValue(true);
        return true;
      }
    }.installOn(list);

    final InstancesWithStackFrameView instancesWithStackFrame = new InstancesWithStackFrameView(session,
      myInstancesTree, list, className);

    add(filteringPane, BorderLayout.NORTH);
    add(instancesWithStackFrame.getComponent(), BorderLayout.CENTER);

    final JComponent focusedComponent = myFilterConditionEditor.getEditorComponent();
    UiNotifyConnector.doWhenFirstShown(focusedComponent, () ->
      IdeFocusManager.findInstanceByComponent(focusedComponent)
        .requestFocus(focusedComponent, true));
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        ApplicationManager.getApplication().invokeLater(() -> cancelFilteringTask());
      }

      @Override
      public void sessionResumed() {
        ApplicationManager.getApplication().invokeLater(() -> getProgress().setVisible(true));
      }
    });
  }

  @Override
  protected InstancesTree getInstancesTree() {
    return myInstancesTree;
  }

  @Override
  public void dispose() {
    cancelFilteringTask();
    Disposer.dispose(myInstancesTree);
  }

  private void updateInstances() {
    cancelFilteringTask();

    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext()) {
      @Override
      public Priority getPriority() {
        return Priority.LOWEST;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        myIsAndroidVM = DebuggerUtils.isAndroidVM(myDebugProcess.getVirtualMachineProxy().getVirtualMachine());
        final int limit = myIsAndroidVM
                          ? AndroidUtil.ANDROID_INSTANCES_LIMIT
                          : DEFAULT_INSTANCES_LIMIT;
        List<JavaReferenceInfo> instances = ContainerUtil
          .map(getInstancesProvider().getInstances(limit + 1), referenceInfo -> ((JavaReferenceInfo)referenceInfo));

        final EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());

        if (instances.size() > limit) {
          myWarningMessageConsumer.accept(XDebuggerBundle.message("memory.view.instances.warning.not.all.loaded", limit));
          instances = instances.subList(0, limit);
        }

        if (Registry.is("debugger.memory.agent.use.in.memory.view")) {
          instances = MemoryAgentUtil.tryCalculateSizes(evaluationContext, instances);
        }

        synchronized (myFilteringTaskLock) {
          List<JavaReferenceInfo> finalInstances = instances;
          ApplicationManager.getApplication().runReadAction(() -> {
            myFilteringTask =
              new FilteringTask(myClassName, myDebugProcess, myFilterConditionEditor.getExpression(), new MyValuesList(finalInstances),
                                new MyFilteringCallback(evaluationContext));

            myFilteringTaskFuture = ApplicationManager.getApplication().executeOnPooledThread(myFilteringTask);
          });
        }
      }
    });
  }

  private void cancelFilteringTask() {
    if (myFilteringTask != null) {
      synchronized (myFilteringTaskLock) {
        if (myFilteringTask != null) {
          myFilteringTask.cancel();
          myFilteringTask = null;
          myFilteringTaskFuture.cancel(false);
          myFilteringTaskFuture = null;
        }
      }
    }
  }


  public JButton getFilterButton() {
    return myFilterButton;
  }

  public FilteringProgressView getProgress() {
    return myProgress;
  }

  private final static class MyNodeManager extends NodeManagerImpl {
    MyNodeManager(Project project) {
      super(project, null);
    }

    @NotNull
    @Override
    public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
      return new DebuggerTreeNodeImpl(null, descriptor);
    }

    @Override
    public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
      return new DebuggerTreeNodeImpl(null, descriptor);
    }

    @NotNull
    @Override
    public DebuggerTreeNodeImpl createMessageNode(String message) {
      return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
    }
  }


  private class MyFilteringCallback implements FilteringTaskCallback {
    private final ErrorsValueGroup myErrorsGroup = new ErrorsValueGroup();
    private final EvaluationContextImpl myEvaluationContext;

    private long myFilteringStartedTime;

    private int myProceedCount;
    private int myMatchedCount;
    private int myErrorsCount;

    private long myLastTreeUpdatingTime;
    private long myLastProgressUpdatingTime;

    MyFilteringCallback(@NotNull EvaluationContextImpl evaluationContext) {
      myEvaluationContext = evaluationContext;
    }

    private XValueChildrenList myChildren = new XValueChildrenList();

    @Override
    public void started(int total) {
      myFilteringStartedTime = System.nanoTime();
      myLastTreeUpdatingTime = myFilteringStartedTime;
      myLastProgressUpdatingTime = System.nanoTime();
      ApplicationManager.getApplication().invokeLater(() -> getProgress().start(total));
    }

    @NotNull
    @Override
    public Action matched(@NotNull JavaReferenceInfo ref) {
      final JavaValue val = new InstanceJavaValue(ref.createDescriptor(myDebugProcess.getProject()),
                                                  myEvaluationContext, myNodeManager);
      myMatchedCount++;
      myProceedCount++;
      myChildren.add(val);
      updateProgress();
      updateTree();

      return myMatchedCount < MAX_TREE_NODE_COUNT ? Action.CONTINUE : Action.STOP;
    }

    @NotNull
    @Override
    public Action notMatched(@NotNull JavaReferenceInfo ref) {
      myProceedCount++;
      updateProgress();

      return Action.CONTINUE;
    }

    @NotNull
    @Override
    public Action error(@NotNull JavaReferenceInfo ref, @NotNull String description) {
      final JavaValue val = new InstanceJavaValue(ref.createDescriptor(myDebugProcess.getProject()),
                                                  myEvaluationContext, myNodeManager);
      myErrorsGroup.addErrorValue(description, val);
      myProceedCount++;
      myErrorsCount++;
      updateProgress();
      return Action.CONTINUE;
    }

    @Override
    public void completed(@NotNull FilteringResult reason) {
      if (!myErrorsGroup.isEmpty()) {
        myChildren.addBottomGroup(myErrorsGroup);
      }

      final long duration = System.nanoTime() - myFilteringStartedTime;
      LOG.info(String.format("Filtering completed in %d ms for %d instances",
                             TimeUnit.NANOSECONDS.toMillis(duration),
                             myProceedCount));

      final int proceed = myProceedCount;
      final int matched = myMatchedCount;
      final int errors = myErrorsCount;
      final XValueChildrenList childrenList = myChildren;
      ApplicationManager.getApplication().invokeLater(() -> {
        getProgress().updateProgress(proceed, matched, errors);
        myInstancesTree.addChildren(childrenList, true);
        getFilterButton().setEnabled(true);
        getProgress().complete(reason);
      });
    }

    private void updateProgress() {
      final long now = System.nanoTime();
      if (now - myLastProgressUpdatingTime > TimeUnit.MILLISECONDS.toNanos(FILTERING_PROGRESS_UPDATING_MIN_DELAY_MILLIS)) {
        final int proceed = myProceedCount;
        final int matched = myMatchedCount;
        final int errors = myErrorsCount;
        ApplicationManager.getApplication().invokeLater(() -> getProgress().updateProgress(proceed, matched, errors));
        myLastProgressUpdatingTime = now;
      }
    }

    private void updateTree() {
      final long now = System.nanoTime();
      final int newChildrenCount = myChildren.size();
      if (newChildrenCount >= FILTERING_CHUNK_SIZE ||
          (newChildrenCount > 0 && now - myLastTreeUpdatingTime > TimeUnit.SECONDS.toNanos(MAX_DURATION_TO_UPDATE_TREE_SECONDS))) {
        final XValueChildrenList children = myChildren;
        ApplicationManager.getApplication().invokeLater(() -> myInstancesTree.addChildren(children, false));
        myChildren = new XValueChildrenList();
        myLastTreeUpdatingTime = System.nanoTime();
      }
    }
  }

  private static class MyValuesList implements FilteringTask.ValuesList {
    private final List<? extends JavaReferenceInfo> myRefs;

    MyValuesList(List<? extends JavaReferenceInfo> refs) {
      myRefs = refs;
    }

    @Override
    public int size() {
      return myRefs.size();
    }

    @Override
    public JavaReferenceInfo get(int index) {
      return myRefs.get(index);
    }
  }
}
