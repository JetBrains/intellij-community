// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class RecentChangesPopup {
  public static void show(Project project, @NotNull IdeaGateway gw, @NotNull LocalHistoryFacade vcs) {
    List<RecentChange> cc = ProgressManager.getInstance().run(new Task.WithResult<>(project,
                                                                                    LocalHistoryBundle.message("recent.changes.loading"),
                                                                                    true) {
      @Override
      protected List<RecentChange> compute(@NotNull ProgressIndicator indicator) {
        return vcs.getRecentChanges(ReadAction.compute(() -> {
          return gw.createTransientRootEntry();
        }));
      }
    });
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

  private static final class RecentChangesListCellRenderer implements ListCellRenderer<RecentChange> {
    private final JPanel myPanel = new JPanel(new FlowLayout(FlowLayout.LEADING,UIUtil.DEFAULT_HGAP,2));
    private final JLabel myActionLabel = new JLabel("", JLabel.LEFT);
    private final JLabel myDateLabel = new JLabel("", JLabel.LEFT);
    private final JPanel mySpacePanel = new JPanel();

    RecentChangesListCellRenderer() {
      myPanel.add(myDateLabel);
      myPanel.add(mySpacePanel);
      myPanel.add(myActionLabel);

      Dimension d = new Dimension(10, mySpacePanel.getPreferredSize().height);
      mySpacePanel.setMinimumSize(d);
      mySpacePanel.setMaximumSize(d);
      mySpacePanel.setPreferredSize(d);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends RecentChange> list,
                                                  @NotNull RecentChange value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      myActionLabel.setText(value.getChangeName());
      myDateLabel.setText(DateFormatUtil.formatDateTime(value.getTimestamp()));

      updateColors(isSelected);
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground(true) : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground(true) : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myActionLabel, myDateLabel, mySpacePanel);
    }

    private static void setColors(Color bg, Color fg, JComponent @NotNull ... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }
}
