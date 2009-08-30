/*
 * Class InspectPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InspectPanel extends DebuggerTreePanel {
  public InspectPanel(Project project, DebuggerStateManager stateManager, @NotNull NodeDescriptorImpl inspectDescriptor) {
    super(project, stateManager);

    getInspectTree().setInspectDescriptor(inspectDescriptor);

    add(new JScrollPane(getInspectTree()), BorderLayout.CENTER);
    final Disposable disposable = DebuggerAction.installEditAction(getInspectTree(), DebuggerActions.EDIT_NODE_SOURCE);
    registerDisposable(disposable);
    setUpdateEnabled(true);
  }


  protected DebuggerTree createTreeView() {
    return new InspectDebuggerTree(getProject());
  }

  protected ActionPopupMenu createPopupMenu() {
    return InspectDebuggerTree.createPopupMenu();
  }

  public InspectDebuggerTree getInspectTree() {
    return (InspectDebuggerTree)getTree();
  }
}