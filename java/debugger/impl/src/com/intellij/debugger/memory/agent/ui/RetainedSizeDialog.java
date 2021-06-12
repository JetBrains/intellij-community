// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
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

public class RetainedSizeDialog extends DialogWrapper {
  private static final Icon HELD_OBJECTS_MARK_ICON = AllIcons.Nodes.Locked;
  public static final Color HELD_OBJECTS_BACKGROUND_COLOR;
  static {
    Color background = UIUtil.getTreeSelectionBackground(true);
    HELD_OBJECTS_BACKGROUND_COLOR = new JBColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 30),
                                                new Color(background.getRed(), background.getGreen(), background.getBlue(), 30));
  }

  private final boolean myRebuildOnSessionEvents;
  private final Set<ObjectReference> myHeldObjects;
  private final HighlightableTree myTree;
  private final BorderLayoutPanel myPanel;
  private final JProgressBar myProgressBar;
  private final NodeHighlighter myHighlighter;
  private final String myRootName;
  private final JBLabel myInfoLabel;
  private final JBLabel myRetainedSizeLabel;

  public RetainedSizeDialog(@NotNull Project project,
                            XDebuggerEditorsProvider editorsProvider,
                            XSourcePosition sourcePosition,
                            @NotNull String name,
                            @NotNull XValue value,
                            XValueMarkers<?, ?> markers,
                            @Nullable XDebugSession session,
                            boolean rebuildOnSessionEvents) {
    super(project, false);
    myRebuildOnSessionEvents = rebuildOnSessionEvents;
    setTitle(JavaDebuggerBundle.message("action.calculate.retained.size.title", name));
    setModal(false);

    myTree = new HighlightableTree(project, editorsProvider, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, markers);
    configureTree(value, name);
    myHighlighter = new NodeHighlighter();
    myTree.addTreeListener(myHighlighter);

    myHeldObjects = new HashSet<>();
    myRootName = name;

    JBPanel topPanel = new JBPanel<>();
    topPanel.setLayout(new VerticalFlowLayout());
    myRetainedSizeLabel = new JBLabel(JavaDebuggerBundle.message("action.calculate.retained.size.waiting.message"));
    myInfoLabel = new JBLabel();

    topPanel.add(myRetainedSizeLabel);
    topPanel.add(myInfoLabel);
    myProgressBar = new JProgressBar();
    myProgressBar.setVisible(false);
    topPanel.add(myProgressBar);

    myPanel = JBUI.Panels.simplePanel()
      .addToCenter(ScrollPaneFactory.createScrollPane(myTree))
      .addToTop(topPanel);

    if (session != null) {
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
          if (myRebuildOnSessionEvents) {
            myTree.invokeLater(() -> myTree.rebuildAndRestore(XDebuggerTreeState.saveState(myTree)));
          }
        }

        @Override
        public void sessionResumed() {
          close(DialogWrapper.OK_EXIT_CODE);
        }
      }, myDisposable);
    }

    init();
  }

  public void setCalculationTimeout() {
    myRetainedSizeLabel.setText(JavaDebuggerBundle.message("debugger.memory.agent.timeout.error"));
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
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @Nullable
  protected JComponent createSouthPanel() {
    return null;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "#javadebugger.RetainedSizeDialog";
  }

  private void configureTree(@NotNull XValue value, @NotNull String name) {
    final XValueNodeImpl root = new XValueNodeImpl(myTree, null, name, value);
    myTree.setRoot(root, true);
    myTree.setSelectionRow(0);
    myTree.expandNodesOnLoad(node -> node == root);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private void highlightLoadedChildren() {
    Stack<XValueNodeImpl> nodes = new Stack<>();
    XValueNodeImpl parent = (XValueNodeImpl)myTree.getRoot();
    nodes.push(parent);
    while (!nodes.empty()) {
      XValueNodeImpl node = nodes.pop();
      for (TreeNode child : node.getLoadedChildren()) {
        if (child instanceof XValueNodeImpl) {
          XValueNodeImpl childImpl = (XValueNodeImpl)child;
          if (myHeldObjects.contains(getObjectReference(childImpl))) {
            myHighlighter.highlightNode(childImpl);
            nodes.push(childImpl);
          }
        }
      }
    }
  }

  public ProgressIndicator createProgressIndicator() {
    return new ProgressIndicatorBase() {
      @Override
      public void setText(String text) {
        super.setText(text);
        myInfoLabel.setText(text);
      }

      @Override
      public void setFraction(double fraction) {
        super.setFraction(fraction);
        myProgressBar.setMinimum(0);
        myProgressBar.setMaximum(100);
        myProgressBar.setValue((int)(fraction * 100));
      }

      @Override
      public void start() {
        super.start();
        myProgressBar.setVisible(true);
      }

      @Override
      public void stop() {
        super.stop();
        myProgressBar.setVisible(false);
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
      if (!mySkipNotification && node instanceof XValueNodeImpl) {
        XValueNodeImpl nodeImpl = (XValueNodeImpl)node;
        if (nodeImpl != nodeImpl.getTree().getRoot() && myHeldObjects.contains(getObjectReference(nodeImpl))) {
          XValuePresentation presentation = nodeImpl.getValuePresentation();
          if (presentation != null && nodeImpl.getIcon() != PlatformDebuggerImplIcons.PinToTop.UnpinnedItem) {
            highlightNode(nodeImpl);
          }
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
          myCachedIcons.computeIfAbsent(icon, nodeIcon -> new LayeredIcon(nodeIcon, HELD_OBJECTS_MARK_ICON)),
          presentation,
          !node.isLeaf()
        );
        myTree.addColoredPath(node.getPath(), HELD_OBJECTS_BACKGROUND_COLOR);
      }
    }
  }
}
