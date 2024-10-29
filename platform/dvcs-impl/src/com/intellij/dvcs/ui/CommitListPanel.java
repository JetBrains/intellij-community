// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * A table with the list of commits.
 *
 * @author Kirill Likhodedov
 */
public class CommitListPanel extends JPanel implements UiDataProvider {

  private final List<VcsFullCommitDetails> myCommits;
  private final TableView<VcsFullCommitDetails> myTable;

  public CommitListPanel(@NotNull List<? extends VcsFullCommitDetails> commits, @Nullable @NlsContexts.Label String emptyText) {
    myCommits = new ArrayList<>(commits);

    myTable = new TableView<>();
    updateModel();
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTable.setStriped(true);
    if (emptyText != null) {
      myTable.getEmptyText().setText(emptyText);
    }

    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable));
  }

  /**
   * Adds a listener that would be called once user selects a commit in the table.
   */
  public void addListSelectionListener(final @NotNull Consumer<? super VcsFullCommitDetails> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        int i = lsm.getMaxSelectionIndex();
        int j = lsm.getMinSelectionIndex();
        if (i >= 0 && i == j) {
          listener.consume(myCommits.get(i));
        }
      }
    });
  }

  public void addListMultipleSelectionListener(final @NotNull Consumer<? super List<Change>> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        List<VcsFullCommitDetails> commits = myTable.getSelectedObjects();

        final List<Change> changes = new ArrayList<>();
        // We need changes in asc order for zipChanges, and they are in desc order in Table
        ListIterator<VcsFullCommitDetails> iterator = commits.listIterator(commits.size());
        while (iterator.hasPrevious()) {
          changes.addAll(iterator.previous().getChanges());
        }

        listener.consume(CommittedChangesTreeBrowser.zipChanges(changes));
      }
    });
  }

  /**
   * Registers the diff action which will be called when the diff shortcut is pressed in the table.
   */
  public void registerDiffAction(@NotNull AnAction diffAction) {
    diffAction.registerCustomShortcutSet(diffAction.getShortcutSet(), myTable);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    // Make changes available for diff action
    int[] rows = myTable.getSelectedRows();
    if (rows.length == 1) {
      sink.set(VcsDataKeys.CHANGES, myCommits.get(rows[0])
        .getChanges().toArray(Change.EMPTY_CHANGE_ARRAY));
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTable;
  }

  public void clearSelection() {
    myTable.clearSelection();
  }

  public void setCommits(@NotNull List<? extends VcsFullCommitDetails> commits) {
    myCommits.clear();
    myCommits.addAll(commits);
    updateModel();
    myTable.repaint();
  }

  private void updateModel() {
    myTable.setModelAndUpdateColumns(new ListTableModel<>(generateColumnsInfo(myCommits), myCommits, 0));
  }

  private ColumnInfo @NotNull [] generateColumnsInfo(@NotNull List<? extends VcsFullCommitDetails> commits) {
    ItemAndWidth hash = new ItemAndWidth("", 0);
    ItemAndWidth author = new ItemAndWidth("", 0);
    ItemAndWidth time = new ItemAndWidth("", 0);
    for (VcsFullCommitDetails commit : commits) {
      hash = getMax(hash, getHash(commit));
      author = getMax(author, getAuthor(commit));
      time = getMax(time, getTime(commit));
    }

    return new ColumnInfo[] {
      new CommitColumnInfo(DvcsBundle.message("column.commit.list.hash"), hash.myItem) {
        @Override
        public String valueOf(VcsFullCommitDetails commit) {
          return getHash(commit);
        }
      },
      new ColumnInfo<VcsFullCommitDetails, String>(DvcsBundle.message("column.commit.list.subject")) {
        @Override
        public String valueOf(VcsFullCommitDetails commit) {
          return commit.getSubject();
        }
      },
      new CommitColumnInfo(DvcsBundle.message("column.commit.list.author"), author.myItem) {
        @Override
        public String valueOf(VcsFullCommitDetails commit) {
          return getAuthor(commit);
        }
      },
      new CommitColumnInfo(DvcsBundle.message("column.commit.list.author.time"), time.myItem) {
        @Override
        public String valueOf(VcsFullCommitDetails commit) {
          return getTime(commit);
        }
      }
    };
  }

  private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
    int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(candidate);
    if (width > current.myWidth) {
      return new ItemAndWidth(candidate, width);
    }
    return current;
  }

  private static final class ItemAndWidth {
    private final String myItem;
    private final int myWidth;

    private ItemAndWidth(String item, int width) {
      myItem = item;
      myWidth = width;
    }
  }

  private static String getHash(VcsFullCommitDetails commit) {
    return DvcsUtil.getShortHash(commit.getId().toString());
  }

  private static String getAuthor(VcsFullCommitDetails commit) {
    return VcsUserUtil.getShortPresentation(commit.getAuthor());
  }

  private static String getTime(VcsFullCommitDetails commit) {
    return DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime());
  }

  private abstract static class CommitColumnInfo extends ColumnInfo<VcsFullCommitDetails, String> {

    @NotNull private final String myMaxString;

    CommitColumnInfo(@NotNull @NlsContexts.ColumnName String name, @NotNull String maxString) {
      super(name);
      myMaxString = maxString;
    }

    @Override
    public String getMaxStringValue() {
      return myMaxString;
    }

    @Override
    public int getAdditionalWidth() {
      return UIUtil.DEFAULT_HGAP;
    }
  }

}
