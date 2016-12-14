package org.jetbrains.debugger.memory.view;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ObjectReference;
import icons.MemoryViewIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.CreationPositionTracker;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.event.InstancesTrackerListener;
import org.jetbrains.debugger.memory.tracking.TrackingType;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class InstancesWithStackFrameView {
  private static final float DEFAULT_SPLITTER_PROPORTION = 0.7f;
  private static final String EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED = "Select instance to see stack frame";
  private static final String EMPTY_TEXT_WHEN_STACK_NOT_FOUND = "No stack frame for this instance";
  private static final String TEXT_FOR_ARRAYS = "Arrays could not be tracked";
  private static final List<StackFrameDescriptor> EMPTY_FRAME = Collections.emptyList();

  private float myHidedProportion;

  private final JBSplitter mySplitter = new JBSplitter(false, DEFAULT_SPLITTER_PROPORTION);
  private boolean myIsHided = false;

  InstancesWithStackFrameView(@NotNull XDebugSession debugSession, @NotNull InstancesTree tree,
                              @NotNull StackFrameList list, String className) {
    mySplitter.setFirstComponent(new JBScrollPane(tree));

    list.setEmptyText(EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED);
    JLabel stackTraceLabel;
    if (isArrayType(className)) {
      stackTraceLabel = new JBLabel(TEXT_FOR_ARRAYS, SwingConstants.CENTER);
    } else {
      ActionLink actionLink = new ActionLink("Enable tracking for new instances", MemoryViewIcons.CLASS_TRACKED, new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          InstancesTracker.getInstance(debugSession.getProject()).add(className, TrackingType.CREATION);
        }
      });

      actionLink.setHorizontalAlignment(SwingConstants.CENTER);
      actionLink.setPaintUnderline(false);
      stackTraceLabel = actionLink;
    }

    mySplitter.setSplitterProportionKey("InstancesWithStackFrameView.SplitterKey");

    JComponent stackComponent = new JBScrollPane(list);

    CreationPositionTracker tracker = CreationPositionTracker.getInstance(debugSession.getProject());
    InstancesTracker instancesTracker = InstancesTracker.getInstance(debugSession.getProject());
    instancesTracker.addTrackerListener(new InstancesTrackerListener() {
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

    mySplitter.setSecondComponent(instancesTracker.isTracked(className) ? stackComponent : stackTraceLabel);

    mySplitter.setHonorComponentsMinimumSize(false);
    myHidedProportion = DEFAULT_SPLITTER_PROPORTION;

    tree.addTreeSelectionListener(e -> {
      ObjectReference ref = tree.getSelectedReference();
      if (ref != null && tracker != null) {
        List<StackFrameDescriptor> stack = tracker.getStack(debugSession, ref);
        if (stack != null) {
          list.setFrame(stack);
          if(mySplitter.getProportion() == 1.f) {
            mySplitter.setProportion(DEFAULT_SPLITTER_PROPORTION);
          }
          return;
        }
        list.setEmptyText(EMPTY_TEXT_WHEN_STACK_NOT_FOUND);
      } else {
        list.setEmptyText(EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED);
      }

      list.setFrame(EMPTY_FRAME);
    });
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
