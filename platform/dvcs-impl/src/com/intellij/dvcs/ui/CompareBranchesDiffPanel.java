/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.ui;

import com.intellij.dvcs.branch.DvcsBranchUtil;
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.dvcs.util.LocalCommitCompareInfo;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Kirill Likhodedov
 */
class CompareBranchesDiffPanel extends JPanel {
  private final CompareBranchesHelper myHelper;
  private final String myBranchName;
  private final Project myProject;
  private final String myCurrentBranchName;
  private final CommitCompareInfo myCompareInfo;
  private final DvcsCompareSettings myVcsSettings;

  private final JBLabel myLabel;
  private final MyChangesBrowser myChangesBrowser;

  public CompareBranchesDiffPanel(CompareBranchesHelper helper, String branchName, String currentBranchName, CommitCompareInfo compareInfo) {
    myHelper = helper;
    myProject = helper.getProject();
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myBranchName = branchName;
    myVcsSettings = helper.getDvcsCompareSettings();

    myLabel = new JBLabel();
    myChangesBrowser = new MyChangesBrowser(helper.getProject(), emptyList());

    HyperlinkLabel swapSidesLabel = new HyperlinkLabel("Swap branches");
    swapSidesLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
        myVcsSettings.setSwapSidesInCompareBranches(!swapSides);
        refreshView();
      }
    });

    JPanel topPanel = new JPanel(new HorizontalLayout(JBUI.scale(10)));
    topPanel.add(myLabel);
    topPanel.add(swapSidesLabel);

    setLayout(new BorderLayout(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
    add(topPanel, BorderLayout.NORTH);
    add(myChangesBrowser);

    refreshView();
  }

  private void refreshView() {
    boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();

    String currentBranchText = String.format("current working tree on <b><code>%s</code></b>", myCurrentBranchName);
    String otherBranchText = String.format("files in <b><code>%s</code></b>", myBranchName);
    myLabel.setText(String.format("<html>Difference between %s and %s:</html>",
                                  swapSides ? otherBranchText : currentBranchText,
                                  swapSides ? currentBranchText : otherBranchText));

    List<Change> diff = myCompareInfo.getTotalDiff();
    if (swapSides) diff = DvcsBranchUtil.swapRevisions(diff);
    myChangesBrowser.setChangesToDisplay(diff);
  }

  private class MyChangesBrowser extends SimpleChangesBrowser {
    public MyChangesBrowser(@NotNull Project project, @NotNull List<Change> changes) {
      super(project, false, true);
      setChangesToDisplay(changes);
    }

    @Override
    public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
      List<Change> oldSelection = getSelectedChanges();
      super.setChangesToDisplay(changes);
      myViewer.setSelectedChanges(DvcsBranchUtil.swapRevisions(oldSelection));
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      return ContainerUtil.append(
        super.createToolbarActions(),
        new MyCopyChangesAction()
      );
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        new MyCopyChangesAction()
      );
    }
  }

  private class MyCopyChangesAction extends DumbAwareAction {
    public MyCopyChangesAction() {
      super("Get from Branch", "Replace file content with its version from branch " + myBranchName, AllIcons.Actions.Download);
      copyShortcutFrom(ActionManager.getInstance().getAction("Vcs.GetVersion"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isEnabled = !myChangesBrowser.getSelectedChanges().isEmpty();
      boolean isVisible = myCompareInfo instanceof LocalCommitCompareInfo;
      e.getPresentation().setEnabled(isEnabled && isVisible);
      e.getPresentation().setVisible(isVisible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String title = String.format("Get from Branch '%s'", myBranchName);
      List<Change> changes = myChangesBrowser.getSelectedChanges();
      boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();

      ReplaceFileConfirmationDialog confirmationDialog = new ReplaceFileConfirmationDialog(myProject, title);
      if (!confirmationDialog.confirmFor(ChangesUtil.getFilesFromChanges(changes))) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      LocalHistoryAction action = LocalHistory.getInstance().startAction(title);

      new Task.Modal(myProject, "Loading Content from Branch", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            ((LocalCommitCompareInfo)myCompareInfo).copyChangesFromBranch(changes, swapSides);
          }
          catch (VcsException err) {
            ApplicationManager.getApplication().invokeLater(() -> {
              Messages.showErrorDialog(myProject, err.getMessage(), "Can't Copy Changes");
            });
          }
        }

        @Override
        public void onFinished() {
          action.finish();

          refreshView();
        }
      }.queue();
    }
  }
}
