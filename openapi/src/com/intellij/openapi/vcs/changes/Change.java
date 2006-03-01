package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;

/**
 * @author max
 */
public class Change {
  public enum Type {
    MODIFICATION,
    NEW,
    DELETED,
    MOVED
  }

  private final ContentRevision myBeforeRevision;
  private final ContentRevision myAfterRevision;

  public Change(final ContentRevision beforeRevision, final ContentRevision afterRevision) {
    myBeforeRevision = beforeRevision;
    myAfterRevision = afterRevision;
  }

  public Type getType() {
    if (myBeforeRevision == null) {
      return Type.NEW;
    }

    if (myAfterRevision == null) {
      return Type.DELETED;
    }

    if (!Comparing.equal(myBeforeRevision.getFile().getPath(), myAfterRevision.getFile().getPath())) {
      return Type.MOVED;
    }

    return Type.MODIFICATION;
  }


  public ContentRevision getBeforeRevision() {
    return myBeforeRevision;
  }

  public ContentRevision getAfterRevision() {
    return myAfterRevision;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return ChangeList.changesEqual(this, (Change)o);
  }
}
