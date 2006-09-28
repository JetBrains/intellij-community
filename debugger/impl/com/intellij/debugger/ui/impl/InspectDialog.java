package com.intellij.debugger.ui.impl;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class InspectDialog extends DialogWrapper implements DebuggerContextListener {
  private InspectPanel myInspectView;
  private DebuggerContextImpl myDebuggerContext;

  public InspectDialog(Project project, DebuggerStateManager stateManager, String title, NodeDescriptorImpl inspectDescriptor) {
    super(project, true);
    setTitle(title);
    setModal(false);

    myDebuggerContext = stateManager.getContext();

    myInspectView = new InspectPanel(project, myDebuggerContext.getDebuggerSession().getContextManager(), inspectDescriptor);
    myInspectView.setBorder(BorderFactory.createEtchedBorder());

    init();

    myDebuggerContext.getDebuggerSession().getContextManager().addListener(this);
    getInspectView().rebuildWhenVisible();
  }

  protected JComponent createCenterPanel() {
    return myInspectView;
  }

  protected JComponent createSouthPanel() {
    return null;
  }

  public void dispose() {
    myDebuggerContext.getDebuggerSession().getContextManager().removeListener(this);
    if (myInspectView != null) {
      myInspectView.dispose();
      myInspectView = null;
    }
    super.dispose();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.impl.InspectDialog";
  }

  public InspectPanel getInspectView() {
    return myInspectView;
  }

  public void changeEvent(DebuggerContextImpl newContext, int event) {
    if(event == DebuggerSession.EVENT_DETACHED) {
      close(CANCEL_EXIT_CODE);
    }
  }
}