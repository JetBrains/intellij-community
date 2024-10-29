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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.vcs.VcsActivity;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

public class CompareBranchesDiffPanel extends JPanel implements UiDataProvider {
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
        swapSides();
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
    refreshView(false);
  }

  @NotNull
  public ChangesBrowserBase getChangesBrowser() {
    return myChangesBrowser;
  }

  private void swapSides() {
    boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
    myVcsSettings.setSwapSidesInCompareBranches(!swapSides);

    refreshView(true);
  }

  private void refreshView(boolean onSwapSides) {
    if (myCompareInfo == null) return;

    boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
    updateLabelText();
    List<Change> diff = myCompareInfo.getTotalDiff();
    if (swapSides) diff = DvcsBranchUtil.swapRevisions(diff);

    if (onSwapSides) {
      myChangesBrowser.setChangesToDisplay(diff, new OnSwapSidesTreeStateStrategy());
    }
    else {
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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(DATA_KEY, this);
  }

  private static class MyChangesBrowser extends SimpleAsyncChangesBrowser {
    MyChangesBrowser(@NotNull Project project, @NotNull List<? extends Change> changes) {
      super(project, false, true);
      setChangesToDisplay(changes);
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
      CompareBranchesDiffPanel panel = e.getData(DATA_KEY);
      if (panel == null) return;

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
      CompareBranchesDiffPanel panel = e.getData(DATA_KEY);
      if (panel == null) return;

      Project project = panel.myProject;
      List<Change> changes = panel.myChangesBrowser.getSelectedChanges();
      boolean swapSides = panel.myVcsSettings.shouldSwapSidesInCompareBranches();
      CommitCompareInfo compareInfo = panel.myCompareInfo;

      String title = DvcsBundle.message("compare.branches.diff.panel.get.from.branch.title", panel.myBranchName);
      ReplaceFileConfirmationDialog confirmationDialog = new ReplaceFileConfirmationDialog(project, title);
      if (!confirmationDialog.confirmFor(ChangesUtil.getFilesFromChanges(changes))) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      LocalHistoryAction action = LocalHistory.getInstance().startAction(VcsBundle.message("activity.name.get.from", panel.myBranchName),
                                                                         VcsActivity.Get);

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

          panel.refreshView(false);
        }
      }.queue();
    }
  }

  private static class OnSwapSidesTreeStateStrategy implements ChangesTree.TreeStateStrategy<OnSwapSidesTreeStateStrategy.MyState> {
    @Override
    public MyState saveState(@NotNull ChangesTree tree) {
      List<Change> changes = VcsTreeModelData.selected(tree).userObjects(Change.class);
      return new MyState(changes);
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, MyState state, boolean scrollToSelection) {
      if (state != null && !state.selectedChanges.isEmpty()) {
        tree.setSelectedChanges(DvcsBranchUtil.swapRevisions(state.selectedChanges));
      }
      else {
        tree.resetTreeState();
      }
    }

    private record MyState(@NotNull List<Change> selectedChanges) {
    }
  }
}
