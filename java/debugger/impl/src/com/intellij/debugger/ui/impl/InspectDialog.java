/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class InspectDialog extends DialogWrapper implements DebuggerContextListener {
  private InspectPanel myInspectView;
  private final DebuggerContextImpl myDebuggerContext;

  public InspectDialog(Project project, DebuggerStateManager stateManager, String title, NodeDescriptorImpl inspectDescriptor) {
    super(project, true);
    setTitle(title);
    setModal(false);

    myDebuggerContext = stateManager.getContext();

    final DebuggerSession session = myDebuggerContext.getDebuggerSession();
    assert session != null;

    myInspectView = new InspectPanel(project, session.getContextManager(), inspectDescriptor);
    myInspectView.setBorder(BorderFactory.createEtchedBorder());

    init();

    session.getContextManager().addListener(this);
    getInspectView().rebuildIfVisible(DebuggerSession.Event.CONTEXT);
  }

  protected JComponent createCenterPanel() {
    return myInspectView;
  }

  protected JComponent createSouthPanel() {
    return null;
  }

  public void dispose() {
    final DebuggerSession session = myDebuggerContext.getDebuggerSession();
    if (session != null) {
      session.getContextManager().removeListener(this);
    }
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

  public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if(event == DebuggerSession.Event.DETACHED) {
      close(CANCEL_EXIT_CODE);
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInspectView.getInspectTree();
  }
}
