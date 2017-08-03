/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.ui;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class RunHotswapDialog extends OptionsDialog {
  private final JPanel myPanel;
  private final ElementsChooser<SessionItem> myElementsChooser;
  private final boolean myDisplayHangWarning;

  public RunHotswapDialog(Project project, List<DebuggerSession> sessions, boolean displayHangWarning) {
    super(project);
    myDisplayHangWarning = displayHangWarning;
    myPanel = new JPanel(new BorderLayout());
    final List<SessionItem> items = new ArrayList<>(sessions.size());
    for (DebuggerSession session : sessions) {
      items.add(new SessionItem(session));
    }
    items.sort(Comparator.comparing(debuggerSession -> debuggerSession.getSession().getSessionName()));
    myElementsChooser = new ElementsChooser<>(items, true);
    myPanel.setBorder(JBUI.Borders.empty(10, 0, 5, 0));
    //myElementsChooser.setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 0, 0));
    if (sessions.size() > 0) {
      myElementsChooser.selectElements(items.subList(0, 1));
    }
    myPanel.add(myElementsChooser, BorderLayout.CENTER);
    //myPanel.add(new JLabel("Choose debug sessions to reload classes:"), BorderLayout.NORTH);
    if(sessions.size() == 1) {
      setTitle(DebuggerBundle.message("hotswap.dialog.title.with.session", sessions.get(0).getSessionName()));
      myPanel.setVisible(false);
    }
    else {
      setTitle(DebuggerBundle.message("hotswap.dialog.title"));
    }
    setButtonsAlignment(SwingConstants.CENTER);
    this.init();
  }

  protected boolean isToBeShown() {
    return DebuggerSettings.RUN_HOTSWAP_ASK.equals(DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE);
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    if (value) {
      DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
    }
    else {
      if (onOk) {
        DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
      }
      else {
        DebuggerSettings.getInstance().RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
      }
    }
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  @NotNull
  protected Action[] createActions(){
    setOKButtonText(CommonBundle.getYesButtonText());
    setCancelButtonText(CommonBundle.getNoButtonText());
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(DebuggerBundle.message("hotswap.dialog.run.prompt"));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = UIUtil.getQuestionIcon();
    label.setIcon(icon);
    label.setIconTextGap(7);
    if (myDisplayHangWarning) {
      final JLabel warningLabel = new JLabel("WARNING! " + DebuggerBundle.message("hotswap.dialog.hang.warning"));
      warningLabel.setUI(new MultiLineLabelUI());
      panel.add(warningLabel, BorderLayout.SOUTH);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Collection<DebuggerSession> getSessionsToReload() {
    final List<SessionItem> markedElements = myElementsChooser.getMarkedElements();
    final List<DebuggerSession>  sessions = new ArrayList<>(markedElements.size());
    for (SessionItem item : markedElements) {
      sessions.add(item.getSession());
    }
    return sessions;
  }

  private static class SessionItem {
    private final DebuggerSession mySession;

    public SessionItem(DebuggerSession session) {
      mySession = session;
    }

    public DebuggerSession getSession() {
      return mySession;
    }

    public String toString() {
      return mySession.getSessionName();
    }
  }
}
