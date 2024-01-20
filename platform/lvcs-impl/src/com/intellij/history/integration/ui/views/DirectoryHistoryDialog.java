// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.views;

import com.intellij.diff.DiffDialogHints;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ExcludingTraversalPolicy;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.history.integration.LocalHistoryBundle.message;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private AsyncChangesTreeImpl<Change> myChangesTree;
  private JScrollPane myChangesTreeScrollPane;
  private ActionToolbar myToolBar;

  public DirectoryHistoryDialog(Project p, IdeaGateway gw, VirtualFile f) {
    this(p, gw, f, true);
  }

  protected DirectoryHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(p, gw, f, doInit);
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new DirectoryHistoryDialogModel(myProject, myGateway, vcs, myFile);
  }

  @Override
  protected Pair<JComponent, Dimension> createDiffPanel(JPanel root, ExcludingTraversalPolicy traversalPolicy) {
    initChangesTree(root);

    JPanel p = new JPanel(new BorderLayout());

    myToolBar = ActionManager.getInstance().createActionToolbar("DirectoryHistoryDiffPanel", createChangesTreeActions(), true);
    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(myToolBar, myChangesTree);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(toolbarPanel, BorderLayout.CENTER);

    if (showSearchField()) {
      SearchTextField search = createSearchBox(root);
      topPanel.add(search, BorderLayout.EAST);
      traversalPolicy.exclude(search.getTextEditor());
    }

    p.add(topPanel, BorderLayout.NORTH);
    p.add(myChangesTreeScrollPane = ScrollPaneFactory.createScrollPane(myChangesTree), BorderLayout.CENTER);

    return Pair.create(p, topPanel.getPreferredSize());
  }

  protected boolean showSearchField() {
    return true;
  }

  @Override
  protected void setDiffBorder(Border border) {
    myChangesTreeScrollPane.setBorder(border);
  }

  private @NotNull SearchTextField createSearchBox(JPanel root) {
    final SearchTextField field = new SearchTextField(getDimensionKey() + ".searchHistory");
    field.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        scheduleRevisionsUpdate(m -> {
          m.setFilter(field.getText());
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!isDisposed()) {
              field.addCurrentTextToHistory();
            }
          });
        });
      }
    });
    DumbAwareAction.create(e -> field.requestFocusInWindow())
      .registerCustomShortcutSet(CommonShortcuts.getFind(), root, this);

    return field;
  }

  private void initChangesTree(JComponent root) {
    myChangesTree = new AsyncChangesTreeImpl.Changes(myProject, false, false);
    myChangesTree.setDoubleClickAndEnterKeyHandler(() -> new ShowDifferenceAction().performIfEnabled());

    new ShowDifferenceAction().registerCustomShortcutSet(root, null);

    myChangesTree.installPopupHandler(createChangesTreeActions());
  }

  private @NotNull ActionGroup createChangesTreeActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new ShowDifferenceAction());
    result.add(new RevertSelectionAction());
    result.add(Separator.getInstance());
    result.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));
    return result;
  }

  @Override
  protected void updateActions() {
    super.updateActions();
    myToolBar.updateActionsImmediately();
  }

  @Override
  protected Runnable doUpdateDiffs(@NotNull DirectoryHistoryDialogModel model) {
    final List<Change> changes = model.getChanges();
    return () -> myChangesTree.setChangesToDisplay(changes);
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.localHistory.show.folder";
  }

  private @NotNull List<DirectoryChange> getDisplayedChanges() {
    return (List)myChangesTree.getDisplayedChanges();
  }

  private @NotNull List<DirectoryChange> getSelectedChanges() {
    return (List)myChangesTree.getSelectedChanges();
  }

  private final class ShowDifferenceAction extends ActionOnSelection {
    ShowDifferenceAction() {
      super(message("action.show.difference"), AllIcons.Actions.Diff);
      setShortcutSet(CommonShortcuts.getDiff());
    }

    @Override
    protected void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> selected) {
      final Set<DirectoryChange> selectedSet = new HashSet<>(selected);

      int index = 0;
      List<Change> changes = new ArrayList<>();
      for (DirectoryChange change : iterFileChanges()) {
        if (selectedSet.contains(change)) index = changes.size();
        changes.add(change);
      }

      ShowDiffAction.showDiffForChange(myProject, changes, index, new ShowDiffContext(DiffDialogHints.FRAME));
    }

    private @NotNull List<DirectoryChange> iterFileChanges() {
      return ContainerUtil.filter(getDisplayedChanges(), each -> each.canShowFileDifference());
    }

    @Override
    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return ContainerUtil.exists(getDisplayedChanges(), each -> each.canShowFileDifference());
    }
  }

  private final class RevertSelectionAction extends ActionOnSelection {
    RevertSelectionAction() {
      super(message("action.revert.selection"), AllIcons.Actions.Rollback);
    }

    @Override
    protected void doPerform(@NotNull DirectoryHistoryDialogModel model, @NotNull List<DirectoryChange> selected) {
      List<Difference> diffs = new ArrayList<>();
      for (DirectoryChange each : selected) {
        diffs.add(each.getModel().getDifference());
      }
      revert(model.createRevisionReverter(diffs));
    }

    @Override
    protected boolean isEnabledFor(@NotNull DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return model.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends MyAction {
    ActionOnSelection(@NlsActions.ActionText String name, Icon icon) {
      super(name, null, icon);
    }

    @Override
    protected void doPerform(DirectoryHistoryDialogModel model) {
      doPerform(model, getSelectedChanges());
    }

    protected abstract void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> selected);

    @Override
    protected boolean isEnabled(DirectoryHistoryDialogModel model) {
      final List<DirectoryChange> changes = getSelectedChanges();
      if (changes.isEmpty()) return false;
      return isEnabledFor(model, changes);
    }

    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return true;
    }
  }
}
