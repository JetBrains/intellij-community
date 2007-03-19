package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.DebuggerView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectCollectedException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VariablesPanel extends DebuggerTreePanel implements DataProvider{

  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  public VariablesPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    setBorder(null);


    final FrameDebuggerTree frameTree = getFrameTree();

    add(new JScrollPane(frameTree), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(frameTree, DebuggerActions.SET_VALUE, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
    overrideShortcut(frameTree, DebuggerActions.MARK_OBJECT, KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
  }

  private void overrideShortcut(final JComponent forComponent, final String actionId, final KeyStroke keyStroke) {
    final AnAction setValueAction  = ActionManager.getInstance().getAction(actionId);
    setValueAction.registerCustomShortcutSet(new CustomShortcutSet(keyStroke), forComponent);
    registerDisposable(new Disposable() {
      public void dispose() {
        setValueAction.unregisterCustomShortcutSet(forComponent);
      }
    });
  }


  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }


  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }


  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

}