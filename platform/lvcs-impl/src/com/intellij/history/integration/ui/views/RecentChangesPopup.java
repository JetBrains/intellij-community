/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RecentChangesPopup {
  public static void show(Project project, IdeaGateway gw, LocalHistoryFacade vcs) {
    List<RecentChange> cc = vcs.getRecentChanges(gw.createTransientRootEntry());
    String title = LocalHistoryBundle.message("recent.changes.popup.title");
    if (cc.isEmpty()) {
      Messages.showInfoMessage(project, LocalHistoryBundle.message("recent.changes.to.changes"), title);
      return;
    }

    JBPopupFactory.getInstance().createPopupChooserBuilder(cc)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setRenderer(new RecentChangesListCellRenderer())
      .setTitle(title)
      .setItemChosenCallback(change -> new RecentChangeDialog(project, gw, change).show())
      .createPopup()
      .showCenteredInCurrentWindow(project);
  }

  private static class RecentChangesListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel = new JPanel(new BorderLayout());
    private final JLabel myActionLabel = new JLabel("", JLabel.LEFT);
    private final JLabel myDateLabel = new JLabel("", JLabel.RIGHT);
    private final JPanel mySpacePanel = new JPanel();

    public RecentChangesListCellRenderer() {
      myPanel.add(myActionLabel, BorderLayout.WEST);
      myPanel.add(myDateLabel, BorderLayout.EAST);
      myPanel.add(mySpacePanel, BorderLayout.CENTER);

      Dimension d = new Dimension(40, mySpacePanel.getPreferredSize().height);
      mySpacePanel.setMinimumSize(d);
      mySpacePanel.setMaximumSize(d);
      mySpacePanel.setPreferredSize(d);
    }

    public Component getListCellRendererComponent(JList l, Object val, int i, boolean isSelected, boolean cellHasFocus) {
      RecentChange c = (RecentChange)val;
      myActionLabel.setText(c.getChangeName());
      myDateLabel.setText(DateFormatUtil.formatPrettyDateTime(c.getTimestamp()));

      updateColors(isSelected);
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myActionLabel, myDateLabel, mySpacePanel);
    }

    private void setColors(Color bg, Color fg, JComponent... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }
}
