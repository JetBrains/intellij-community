// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.branch.DvcsBranchUtil;
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.dvcs.util.LocalCommitCompareInfo;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
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

public class CompareBranchesDiffPanel extends JPanel implements DataProvider {
  public static final DataKey<CompareBranchesDiffPanel> DATA_KEY = DataKey.create("com.intellij.dvcs.ui.CompareBranchesDiffPanel");

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
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
        myVcsSettings.setSwapSidesInCompareBranches(!swapSides);
        refreshView();
      }
    });
    updateLabelText();

    myChangesBrowser = new MyChangesBrowser(project, emptyList());
    myChangesBrowser.getViewer().setTreeStateStrategy(ChangesTree.KEEP_NON_EMPTY);

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

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  private static class MyChangesBrowser extends SimpleChangesBrowser {
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
        ActionManager.getInstance().getAction("Vcs.GetVersion")
      );
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        ActionManager.getInstance().getAction("Vcs.GetVersion")
      );
    }
  }

  public static class GetVersionActionProvider implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return e.getData(DATA_KEY) != null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      CompareBranchesDiffPanel panel = e.getRequiredData(DATA_KEY);

      Presentation presentation = e.getPresentation();
      presentation.setText(DvcsBundle.messagePointer("compare.branches.diff.panel.get.from.branch.action"));
      presentation.setDescription(DvcsBundle.messagePointer("compare.branches.diff.panel.get.from.branch.action.description",
                                                            panel.myBranchName));

      boolean isEnabled = !panel.myChangesBrowser.getSelectedChanges().isEmpty();
      boolean isVisible = panel.myCompareInfo instanceof LocalCommitCompareInfo;
      presentation.setEnabled(isEnabled && isVisible);
      presentation.setVisible(isVisible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      CompareBranchesDiffPanel panel = e.getRequiredData(DATA_KEY);

      Project project = panel.myProject;
      List<Change> changes = panel.myChangesBrowser.getSelectedChanges();
      boolean swapSides = panel.myVcsSettings.shouldSwapSidesInCompareBranches();
      CommitCompareInfo compareInfo = panel.myCompareInfo;

      String title = DvcsBundle.message("compare.branches.diff.panel.get.from.branch.title", panel.myBranchName);
      ReplaceFileConfirmationDialog confirmationDialog = new ReplaceFileConfirmationDialog(project, title);
      if (!confirmationDialog.confirmFor(ChangesUtil.getFilesFromChanges(changes))) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      LocalHistoryAction action = LocalHistory.getInstance().startAction(title);

      new Task.Modal(project, DvcsBundle.message("compare.branches.diff.panel.loading.content.from.branch.process"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (compareInfo != null) {
              ((LocalCommitCompareInfo)compareInfo).copyChangesFromBranch(changes, swapSides);
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

          panel.refreshView();
        }
      }.queue();
    }
  }
}
