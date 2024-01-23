// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.util.ObjectUtils;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.event.InstancesTrackerListener;
import com.intellij.xdebugger.memory.tracking.TrackingType;
import com.intellij.xdebugger.memory.ui.InstancesTree;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class InstancesWithStackFrameView {
  private static final float DEFAULT_SPLITTER_PROPORTION = 0.7f;

  private float myHidedProportion;

  private final JBSplitter mySplitter = new JBSplitter(false, DEFAULT_SPLITTER_PROPORTION);
  private boolean myIsHided = false;

  InstancesWithStackFrameView(@NotNull XDebugSession debugSession,
                              @NotNull JComponent component,
                              @NotNull InstancesTree tree,
                              @NotNull StackFrameList list, @NotNull String className) {
    mySplitter.setFirstComponent(component);

    final Project project = debugSession.getProject();
    list.setEmptyText(JavaDebuggerBundle.message("status.text.select.instance.to.see.stack.frame"));
    JLabel stackTraceLabel;
    if (isArrayType(className)) {
      stackTraceLabel = new JBLabel(JavaDebuggerBundle.message("label.arrays.could.not.be.tracked"), SwingConstants.CENTER);
    }
    else {
      ActionLink actionLink = new ActionLink(JavaDebuggerBundle.message("enable.tracking.for.new.instances"),
                                             AllIcons.Debugger.Watch,
                                             new AnAction() {
                                               @Override
                                               public void actionPerformed(@NotNull AnActionEvent e) {
                                                 final Project project = e.getProject();
                                                 if (project != null && !project.isDisposed()) {
                                                   InstancesTracker.getInstance(project).add(className, TrackingType.CREATION);
                                                 }
                                               }
                                             });

      actionLink.setHorizontalAlignment(SwingConstants.CENTER);
      actionLink.setPaintUnderline(false);
      stackTraceLabel = actionLink;
    }

    mySplitter.setSplitterProportionKey("InstancesWithStackFrameView.SplitterKey");

    JComponent stackComponent = new JBScrollPane(list);

    if (!project.isDisposed()) {
      final InstancesTracker tracker = InstancesTracker.getInstance(project);
      tracker.addTrackerListener(new InstancesTrackerListener() {
        @Override
        public void classChanged(@NotNull String name, @NotNull TrackingType type) {
          if (Objects.equals(className, name) && type == TrackingType.CREATION) {
            mySplitter.setSecondComponent(stackComponent);
          }
        }

        @Override
        public void classRemoved(@NotNull String name) {
          if (Objects.equals(name, className)) {
            mySplitter.setSecondComponent(stackTraceLabel);
          }
        }
      }, tree);

      mySplitter.setSecondComponent(tracker.isTracked(className) ? stackComponent : stackTraceLabel);
    }

    mySplitter.setHonorComponentsMinimumSize(false);
    myHidedProportion = DEFAULT_SPLITTER_PROPORTION;

    final MemoryViewDebugProcessData data =
      DebuggerManager.getInstance(project).getDebugProcess(debugSession.getDebugProcess().getProcessHandler())
        .getUserData(MemoryViewDebugProcessData.KEY);
    tree.addTreeSelectionListener(e -> {
      ObjectReference ref = getSelectedReference(tree);
      if (ref != null && data != null) {
        List<StackFrameItem> stack = data.getTrackedStacks().getStack(ref);
        if (stack != null) {
          list.setFrameItems(stack);
          if (mySplitter.getProportion() == 1.f) {
            mySplitter.setProportion(DEFAULT_SPLITTER_PROPORTION);
          }
          return;
        }
        list.setEmptyText(JavaDebuggerBundle.message("status.text.no.stack.frame.for.this.instance"));
      }
      else {
        list.setEmptyText(JavaDebuggerBundle.message("status.text.select.instance.to.see.stack.frame"));
      }

      list.setFrameItems(Collections.emptyList());
    });
  }

  @Nullable
  private static ObjectReference getSelectedReference(InstancesTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    Object selectedItem = selectionPath != null ? selectionPath.getLastPathComponent() : null;
    if (selectedItem instanceof XValueNodeImpl xValueNode &&
        xValueNode.getValueContainer() instanceof NodeDescriptorProvider descriptorProvider &&
        descriptorProvider.getDescriptor() instanceof ValueDescriptor valueDescriptor) {
      return ObjectUtils.tryCast(valueDescriptor.getValue(), ObjectReference.class);
    }

    return null;
  }

  JComponent getComponent() {
    return mySplitter;
  }

  private static boolean isArrayType(@NotNull String className) {
    return className.contains("[]");
  }

  @SuppressWarnings("unused")
  private void hideStackFrame() {
    if (!myIsHided) {
      myHidedProportion = mySplitter.getProportion();
      mySplitter.getSecondComponent().setVisible(false);
      mySplitter.setProportion(1.f);
      myIsHided = true;
    }
  }

  @SuppressWarnings("unused")
  private void showStackFrame() {
    if (myIsHided) {
      mySplitter.getSecondComponent().setVisible(true);
      mySplitter.setProportion(myHidedProportion);
      myIsHided = false;
    }
  }
}
