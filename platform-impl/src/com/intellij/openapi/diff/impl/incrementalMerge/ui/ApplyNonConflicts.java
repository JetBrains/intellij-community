package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;

import java.util.ArrayList;
import java.util.Iterator;

public class ApplyNonConflicts extends AnAction {
  public ApplyNonConflicts() {
    super(DiffBundle.message("merge.dialog.apply.all.non.conflicting.changes.action.name"), null, IconLoader.getIcon("/diff/applyNotConflicts.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    ArrayList<Change> notConflicts = ContainerUtil.collect(getNotConflicts(dataContext));
    for (Iterator<Change> iterator = notConflicts.iterator(); iterator.hasNext();) {
      Change change = iterator.next();
      Change.apply(change, MergeList.BRANCH_SIDE);
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getNotConflicts(e.getDataContext()).hasNext());
  }

  private Iterator<Change> getNotConflicts(DataContext dataContext) {
    MergeList mergeList = MergeList.fromDataContext(dataContext);
    if (mergeList == null) return new ArrayList<Change>(1).iterator();
    return FilteringIterator.create(mergeList.getAllChanges(), MergeList.NOT_CONFLICTS);
  }
}
