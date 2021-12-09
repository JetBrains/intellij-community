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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;

public class CompareBranchesDiffPanel extends JPanel {
  private final @NlsSafe String myBranchName;
  private final Project myProject;
  private final @NlsSafe String myCurrentBranchName;
  private final DvcsCompareSettings myVcsSettings;

  @Nullable private CommitCompareInfo myCompareInfo;

  private final JEditorPane myLabel;
  private final MyChangesBrowser myChangesBrowser;

  public CompareBranchesDiffPanel(@NotNull Project project,
                                  @NotNull DvcsCompareSettings settings,
                                  @NotNull String branchName,
                                  @NotNull String currentBranchName) {
    myProject = project;
    myCurrentBranchName = currentBranchName;
    myBranchName = branchName;
    myVcsSettings = settings;

    myLabel = new JEditorPane() {
      @Override
      public void setText(@Nls String t) {
        super.setText(t);
        getPreferredSize();
      }
    };
    myLabel.setEditorKit(HTMLEditorKitBuilder.simple());
    myLabel.setEditable(false);
    myLabel.setBackground(null);
    myLabel.setOpaque(false);
    myLabel.setFocusable(false);
    myLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
        myVcsSettings.setSwapSidesInCompareBranches(!swapSides);
        refreshView();
      }
    });
    updateLabelText();

    myChangesBrowser = new MyChangesBrowser(project, emptyList());

    setLayout(new BorderLayout());
    add(myLabel, BorderLayout.NORTH);
    add(myChangesBrowser, BorderLayout.CENTER);
  }

  @RequiresEdt
  public void setCompareInfo(@NotNull CommitCompareInfo compareInfo) {
    myCompareInfo = compareInfo;
    refreshView();
  }

  @NotNull
  public SimpleChangesBrowser getChangesBrowser() {
    return myChangesBrowser;
  }

  private void refreshView() {
    if (myCompareInfo != null) {
      boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
      updateLabelText();
      List<Change> diff = myCompareInfo.getTotalDiff();
      if (swapSides) diff = DvcsBranchUtil.swapRevisions(diff);
      myChangesBrowser.setChangesToDisplay(diff);
    }
  }

  private void updateLabelText() {
    boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
    HtmlChunk branchNameText = HtmlChunk.text(myBranchName).code().bold();
    HtmlChunk currentBranchNameText = HtmlChunk.text(myCurrentBranchName).code().bold();
    String diffBetween;
    if (swapSides) {
      diffBetween = DvcsBundle.message("compare.branches.diff.panel.diff.between.files.in.branch.and.current.working.tree.on.branch",
                                       branchNameText,
                                       currentBranchNameText);
    }
    else {
      diffBetween = DvcsBundle.message("compare.branches.diff.panel.difference.between.current.working.tree.on.branch.and.files.in.branch",
                                       currentBranchNameText,
                                       branchNameText);
    }

    String swapBranches = DvcsBundle.message("compare.branches.diff.panel.swap.branches");
    myLabel.setText(XmlStringUtil.wrapInHtml(diffBetween + "&emsp;" + HtmlChunk.link("", swapBranches))); // NON-NLS
  }

  public void setEmptyText(@NotNull @NlsContexts.Label String text) {
    myChangesBrowser.getViewer().setEmptyText(text);
  }

  public void disableControls() {
    myLabel.setEnabled(false);
  }

  public void enableControls() {
    myLabel.setEnabled(true);
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myChangesBrowser.getPreferredFocusedComponent();
  }

  private class MyChangesBrowser extends SimpleChangesBrowser {
    MyChangesBrowser(@NotNull Project project, @NotNull List<? extends Change> changes) {
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
    MyCopyChangesAction() {
      super(DvcsBundle.messagePointer("compare.branches.diff.panel.get.from.branch.action"),
            DvcsBundle.messagePointer("compare.branches.diff.panel.get.from.branch.action.description", myBranchName),
            AllIcons.Actions.Download);
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
      String title = DvcsBundle.message("compare.branches.diff.panel.get.from.branch.title", myBranchName);
      List<Change> changes = myChangesBrowser.getSelectedChanges();
      boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();

      ReplaceFileConfirmationDialog confirmationDialog = new ReplaceFileConfirmationDialog(myProject, title);
      if (!confirmationDialog.confirmFor(ChangesUtil.getFilesFromChanges(changes))) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      LocalHistoryAction action = LocalHistory.getInstance().startAction(title);

      new Task.Modal(myProject, DvcsBundle.message("compare.branches.diff.panel.loading.content.from.branch.process"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (myCompareInfo != null) {
              ((LocalCommitCompareInfo)myCompareInfo).copyChangesFromBranch(changes, swapSides);
            }
          }
          catch (VcsException err) {
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myProject, err.getMessage(), DvcsBundle
              .message("compare.branches.diff.panel.can.not.copy.changes.error")));
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
