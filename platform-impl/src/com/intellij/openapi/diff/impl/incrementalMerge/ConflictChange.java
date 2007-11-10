package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.TextRange;

class ConflictChange extends Change implements DiffRangeMarker.RangeInvalidListener {
  private Side myOriginalSide;
  private MergeConflict myConflict;

  public ConflictChange(MergeConflict conflict, FragmentSide mergeSide, TextRange range) {
    myConflict = conflict;
    myOriginalSide = new Side(mergeSide, new DiffRangeMarker(conflict.getOriginalDocument(mergeSide), range, this));
  }

  protected void removeFromList() {
    myConflict.conflictRemoved();
    myConflict = null;
  }

  public ChangeType.ChangeSide getChangeSide(FragmentSide side) {
    return isBranch(side) ? myOriginalSide : (ChangeType.ChangeSide)myConflict;
  }

  private boolean isBranch(FragmentSide side) {
    return MergeList.BRANCH_SIDE == side;
  }

  public Change.Side getOriginalSide() {
    return myOriginalSide;
  }

  public ChangeType getType() { return ChangeType.CONFLICT; }

  public ChangeList getChangeList() {
    return myConflict.getMergeList().getChanges(myOriginalSide.getFragmentSide());
  }

  public void onRemovedFromList() {
    myConflict.onChangeRemoved(myOriginalSide.getFragmentSide(), this);
    myOriginalSide.getRange().removeListener(this);
    myConflict = null;
    myOriginalSide = null;
  }

  public boolean isValid() {
    return myConflict != null;
  }

  public void onRangeInvalidated() {
    removeFromList();
  }
}
