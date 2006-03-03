package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;

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

    final ContentRevision br1 = getBeforeRevision();
    final ContentRevision br2 = ((Change)o).getBeforeRevision();
    final ContentRevision ar1 = getAfterRevision();
    final ContentRevision ar2 = ((Change)o).getAfterRevision();

    FilePath fbr1 = br1 != null ? br1.getFile() : null;
    FilePath fbr2 = br2 != null ? br2.getFile() : null;

    FilePath far1 = ar1 != null ? ar1.getFile() : null;
    FilePath far2 = ar2 != null ? ar2.getFile() : null;

    return Comparing.equal(fbr1, fbr2) && Comparing.equal(far1, far2);
  }

  public int hashCode() {
    return revisionHashCode(getBeforeRevision()) * 27 + revisionHashCode(getAfterRevision());
  }

  private static int revisionHashCode(ContentRevision rev) {
    if (rev == null) return 0;
    return rev.getFile().hashCode();
  }
}
