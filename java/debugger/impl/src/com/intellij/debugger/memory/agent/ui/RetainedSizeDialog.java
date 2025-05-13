// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import icons.PlatformDebuggerImplIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.*;

import static com.intellij.debugger.memory.action.DebuggerTreeAction.getObjectReference;

public final class RetainedSizeDialog extends MemoryAgentDialog {
  private static final Icon HELD_OBJECTS_MARK_ICON = AllIcons.Nodes.Locked;
  public static final Color HELD_OBJECTS_BACKGROUND_COLOR;

  static {
    Color background = UIUtil.getTreeSelectionBackground(true);
    HELD_OBJECTS_BACKGROUND_COLOR = new JBColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 30),
                                                new Color(background.getRed(), background.getGreen(), background.getBlue(), 30));
  }

  private final Set<ObjectReference> myHeldObjects;
  private final NodeHighlighter myHighlighter;
  private final String myRootName;
  private final JBLabel myRetainedSizeLabel;

  public RetainedSizeDialog(@NotNull Project project,
                            XDebuggerEditorsProvider editorsProvider,
                            XSourcePosition sourcePosition,
                            @NotNull String name,
                            @NotNull XValue value,
                            XValueMarkers<?, ?> markers,
                            @Nullable XDebugSession session,
                            boolean rebuildOnSessionEvents) {
    super(
      project, name, value, session,
      new HighlightableTree(project, editorsProvider, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, markers),
      rebuildOnSessionEvents
    );

    setTitle(JavaDebuggerBundle.message("action.calculate.retained.size.title", name));

    myHighlighter = new NodeHighlighter();
    myTree.addTreeListener(myHighlighter);

    myHeldObjects = new HashSet<>();
    myRootName = name;

    myRetainedSizeLabel = new JBLabel(JavaDebuggerBundle.message("action.calculate.retained.size.waiting.message"));

    myTopPanel.add(myRetainedSizeLabel);
  }

  public void setCalculationTimeoutMessage() {
    myRetainedSizeLabel.setText(JavaDebuggerBundle.message("debugger.memory.agent.timeout.error"));
  }

  public void setAgentCouldntBeLoadedMessage() {
    myRetainedSizeLabel.setText(JavaDebuggerBundle.message("debugger.memory.agent.loading.error"));
  }

  public void setHeldObjectsAndSizes(@NotNull Collection<? extends ObjectReference> heldObjects, long shallowSize, long retainedSize) {
    myHeldObjects.clear();
    myHeldObjects.addAll(heldObjects);
    highlightLoadedChildren();
    myRetainedSizeLabel.setText(
      JavaDebuggerBundle.message(
        "action.calculate.retained.size.text",
        myRootName,
        StringUtil.formatFileSize(retainedSize),
        StringUtil.formatFileSize(shallowSize)
      )
    );
    myTree.repaint();
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return "#javadebugger.RetainedSizeDialog";
  }

  private void highlightLoadedChildren() {
    Stack<XValueNodeImpl> nodes = new Stack<>();
    XValueNodeImpl parent = (XValueNodeImpl)myTree.getRoot();
    nodes.push(parent);
    while (!nodes.empty()) {
      XValueNodeImpl node = nodes.pop();
      for (TreeNode child : node.getLoadedChildren()) {
        if (child instanceof XValueNodeImpl childImpl && myHeldObjects.contains(getObjectReference(childImpl))) {
          myHighlighter.highlightNode(childImpl);
          nodes.push(childImpl);
        }
      }
    }
  }

  @Override
  public ProgressIndicator createProgressIndicator() {
    return new MemoryAgentActionProgressIndicator() {
      @Override
      public void stop() {
        super.stop();
        myInfoLabel.setVisible(true);
        myInfoLabel.setText(JavaDebuggerBundle.message("action.calculate.retained.size.info", myRootName));
        myInfoLabel.setIcon(AllIcons.General.Information);
      }
    };
  }

  private class NodeHighlighter implements XDebuggerTreeListener {
    private boolean mySkipNotification;
    private final Map<Icon, Icon> myCachedIcons;

    NodeHighlighter() {
      mySkipNotification = false;
      myCachedIcons = new HashMap<>();
    }

    @Override
    public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
      if (!mySkipNotification && node instanceof XValueNodeImpl nodeImpl &&
          nodeImpl != nodeImpl.getTree().getRoot() && myHeldObjects.contains(getObjectReference(nodeImpl))) {
        XValuePresentation presentation = nodeImpl.getValuePresentation();
        if (presentation != null && nodeImpl.getIcon() != PlatformDebuggerImplIcons.PinToTop.UnpinnedItem) {
          highlightNode(nodeImpl);
        }
      }
      mySkipNotification = false;
    }

    public void highlightNode(@NotNull XValueNodeImpl node) {
      XValuePresentation presentation = node.getValuePresentation();
      Icon icon = node.getIcon();
      if (presentation != null && icon != PlatformDebuggerImplIcons.PinToTop.UnpinnedItem) {
        mySkipNotification = true;
        node.applyPresentation(
          myCachedIcons.computeIfAbsent(icon, nodeIcon -> LayeredIcon.layeredIcon(new Icon[]{nodeIcon, HELD_OBJECTS_MARK_ICON})),
          presentation,
          !node.isLeaf()
        );
        ((HighlightableTree)myTree).addColoredPath(node.getPath(), HELD_OBJECTS_BACKGROUND_COLOR);
      }
    }
  }
}
