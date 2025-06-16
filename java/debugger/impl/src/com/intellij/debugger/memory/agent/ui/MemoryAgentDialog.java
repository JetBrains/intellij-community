// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public abstract class MemoryAgentDialog extends DialogWrapper {
  protected final boolean myRebuildOnSessionEvents;
  protected final XDebuggerTree myTree;
  protected final BorderLayoutPanel myPanel;
  protected final JProgressBar myProgressBar;
  protected final JBLabel myInfoLabel;
  protected final JBPanel myTopPanel;

  MemoryAgentDialog(@NotNull Project project,
                    @NotNull String name,
                    @NotNull XValue value,
                    @Nullable XDebugSession session,
                    @NotNull XDebuggerTree tree,
                    boolean rebuildOnSessionEvents) {
    super(project, false);
    myRebuildOnSessionEvents = rebuildOnSessionEvents;
    setModal(false);

    myTopPanel = new JBPanel<>();
    myTopPanel.setLayout(new VerticalFlowLayout());
    myInfoLabel = new JBLabel();
    myTopPanel.add(myInfoLabel);
    myProgressBar = new JProgressBar();
    myProgressBar.setVisible(false);
    myTopPanel.add(myProgressBar);

    myTree = tree;
    myPanel = JBUI.Panels.simplePanel()
      .addToCenter(ScrollPaneFactory.createScrollPane(myTree))
      .addToTop(myTopPanel);

    XValueNodeImpl root = new XValueNodeImpl(myTree, null, name, value);
    myTree.setRoot(root, true);
    myTree.setSelectionRow(0);
    myTree.expandNodesOnLoad(node -> node == root);

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

  public ProgressIndicator createProgressIndicator() {
    return new MemoryAgentActionProgressIndicator();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected @Nullable JComponent createSouthPanel() {
    return null;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  protected class MemoryAgentActionProgressIndicator extends ProgressIndicatorBase {
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
      myInfoLabel.setVisible(false);
    }
  }
}
