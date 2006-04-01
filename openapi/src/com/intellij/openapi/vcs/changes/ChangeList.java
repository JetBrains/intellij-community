package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class ChangeList implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  private Collection<Change> myChanges = new HashSet<Change>();
  private Collection<Change> myReadChangesCache = null;
  private String myName;
  private String myComment = "";

  private boolean myIsDefault = false;
  private Collection<Change> myOutdatedChanges;
  private boolean myIsInUpdate = false;
  private Set<Change> myChangesBeforeUpdate;

  public static ChangeList createEmptyChangeList(String description) {
    return new ChangeList(description);
  }

  private ChangeList(final String description) {
    myName = description;
  }

  public synchronized Collection<Change> getChanges() {
    if (myReadChangesCache == null) {
      myReadChangesCache = new HashSet<Change>(myChanges);
      if (myOutdatedChanges != null) {
        myReadChangesCache.addAll(myOutdatedChanges);
      }
    }
    return myReadChangesCache;
  }

  public String getName() {
    return myName;
  }


  public String getComment() {
    return myComment;
  }

  public void setComment(final String comment) {
    myComment = comment != null ? comment : "";
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  public synchronized boolean isInUpdate() {
    return myIsInUpdate;
  }

  void setDefault(final boolean isDefault) {
    myIsDefault = isDefault;
  }

  synchronized void addChange(Change change) {
    myReadChangesCache = null;
    myChanges.add(change);
  }

  synchronized void removeChange(Change change) {
    myReadChangesCache = null;
    myChanges.remove(change);
  }

  synchronized void startProcessingChanges(final VcsDirtyScope scope) {
    myChangesBeforeUpdate = new HashSet<Change>(myChanges);
    myOutdatedChanges = new ArrayList<Change>();
    for (Change oldBoy : myChangesBeforeUpdate) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())) {
        removeChange(oldBoy);
        myOutdatedChanges.add(oldBoy);
        myIsInUpdate = true;
      }
    }
    if (isDefault()) {
      myIsInUpdate = true;
    }
  }

  synchronized boolean processChange(Change change) {
    if (myIsDefault) {
      addChange(change);
      return true;
    }

    for (Change oldChange : myOutdatedChanges) {
      if (Comparing.equal(oldChange, change)) {
        addChange(change);
        return true;
      }
    }
    return false;
  }

  synchronized boolean doneProcessingChanges() {
    boolean changesDetected = !Comparing.equal(myChanges, myChangesBeforeUpdate);
    myOutdatedChanges = null;
    myReadChangesCache = null;
    myIsInUpdate = false;
    return changesDetected;
  }

  public synchronized boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ChangeList list = (ChangeList)o;

    if (myIsDefault != list.myIsDefault) return false;
    if (!myChanges.equals(list.myChanges)) return false;
    if (!myName.equals(list.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public ChangeList clone() {
    try {
      return (ChangeList)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }
}
