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

import com.intellij.diff.DiffDialogHints;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.history.integration.LocalHistoryBundle.message;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private ChangesTreeList<Change> myChangesTree;
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

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createChangesTreeActions(root), true);
    JPanel toolBarPanel = new JPanel(new BorderLayout());
    toolBarPanel.add(myToolBar.getComponent(), BorderLayout.CENTER);

    if (showSearchField()) {
      SearchTextField search = createSearchBox(root);
      toolBarPanel.add(search, BorderLayout.EAST);
      traversalPolicy.exclude(search.getTextEditor());
    }

    p.add(toolBarPanel, BorderLayout.NORTH);
    p.add(myChangesTreeScrollPane = ScrollPaneFactory.createScrollPane(myChangesTree), BorderLayout.CENTER);

    return Pair.create((JComponent)p, toolBarPanel.getPreferredSize());
  }

  protected boolean showSearchField() {
    return true;
  }

  @Override
  protected void setDiffBorder(Border border) {
    myChangesTreeScrollPane.setBorder(border);
  }

  private SearchTextField createSearchBox(JPanel root) {
    final SearchTextFieldWithStoredHistory field = new SearchTextFieldWithStoredHistory(getPropertiesKey() + ".searchHistory");
    field.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        scheduleRevisionsUpdate(m -> {
          m.setFilter(field.getText());
          field.addCurrentTextToHistory();
        });
      }
    });

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        field.requestFocusInWindow();
      }
    }.registerCustomShortcutSet(CommonShortcuts.getFind(), root, this);

    return field;
  }

  private void initChangesTree(JComponent root) {
    myChangesTree = createChangesTree();
    myChangesTree.setDoubleClickHandler(() -> new ShowDifferenceAction().performIfEnabled());
    myChangesTree.installPopupHandler(createChangesTreeActions(root));
  }

  private ChangesTreeList<Change> createChangesTree() {
    return new ChangesTreeList<Change>(myProject, Collections.<Change>emptyList(), false, false, null, null) {
      @Override
      protected DefaultTreeModel buildTreeModel(List<Change> cc, ChangeNodeDecorator changeNodeDecorator) {
        return new TreeModelBuilder(myProject, isShowFlatten()).buildModel(cc, changeNodeDecorator);
      }

      @Override
      protected List<Change> getSelectedObjects(ChangesBrowserNode node) {
        return node.getAllChangesUnder();
      }

      protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change)o;
        }
        return null;
      }
    };
  }

  private ActionGroup createChangesTreeActions(JComponent root) {
    DefaultActionGroup result = new DefaultActionGroup();
    ShowDifferenceAction a = new ShowDifferenceAction();
    a.registerCustomShortcutSet(CommonShortcuts.getDiff(), root);
    result.add(a);
    result.add(new RevertSelectionAction());
    result.addSeparator();
    result.addAll(myChangesTree.getTreeActions());
    return result;
  }

  @Override
  protected void updateActions() {
    super.updateActions();
    myToolBar.updateActionsImmediately();
  }

  @Override
  protected Runnable doUpdateDiffs(DirectoryHistoryDialogModel model) {
    final List<Change> changes = model.getChanges();
    return () -> myChangesTree.setChangesToDisplay(changes);
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.localHistory.show.folder";
  }

  private List<DirectoryChange> getChanges() {
    return (List)myChangesTree.getChanges();
  }

  private List<DirectoryChange> getSelectedChanges() {
    return (List)myChangesTree.getSelectedChanges();
  }

  private class ShowDifferenceAction extends ActionOnSelection {
    public ShowDifferenceAction() {
      super(message("action.show.difference"), "/actions/diff.png");
    }

    @Override
    protected void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> selected) {
      final Set<DirectoryChange> selectedSet = new THashSet<>(selected);

      int index = 0;
      List<Change> changes = new ArrayList<>();
      for (DirectoryChange change : iterFileChanges()) {
        if (selectedSet.contains(change)) index = changes.size();
        changes.add(change);
      }

      ShowDiffAction.showDiffForChange(myProject, changes, index, new ShowDiffContext(DiffDialogHints.FRAME));
    }

    private Iterable<DirectoryChange> iterFileChanges() {
      return ContainerUtil.iterate(getChanges(), each -> each.canShowFileDifference());
    }

    @Override
    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return iterFileChanges().iterator().hasNext();
    }
  }

  private class RevertSelectionAction extends ActionOnSelection {
    public RevertSelectionAction() {
      super(message("action.revert.selection"), "/actions/rollback.png");
    }

    @Override
    protected void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> selected) {
      List<Difference> diffs = new ArrayList<>();
      for (DirectoryChange each : selected) {
        diffs.add(each.getModel().getDifference());
      }
      revert(model.createRevisionReverter(diffs));
    }

    @Override
    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return model.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends MyAction {
    public ActionOnSelection(String name, String iconName) {
      super(name, null, IconLoader.getIcon(iconName));
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
