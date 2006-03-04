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
  private String myDescription;
  private boolean myIsDefault = false;
  private Collection<Change> myOutdatedChanges;
  private boolean myIsInUpdate = false;
  private Set<Change> myChangesBeforeUpdate;

  public static ChangeList createEmptyChangeList(String description) {
    return new ChangeList(description);
  }

  private ChangeList(final String description) {
    myDescription = description;
  }

  public Collection<Change> getChanges() {
    if (myReadChangesCache == null) {
      myReadChangesCache = new HashSet<Change>(myChanges);
      if (myOutdatedChanges != null) {
        myReadChangesCache.addAll(myOutdatedChanges);
      }
    }
    return myReadChangesCache;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  public boolean isInUpdate() {
    return myIsInUpdate;
  }

  void setDefault(final boolean isDefault) {
    myIsDefault = isDefault;
  }

  void addChange(Change change) {
    myReadChangesCache = null;
    myChanges.add(change);
  }

  void removeChange(Change change) {
    myReadChangesCache = null;
    myChanges.remove(change);
  }

  void startProcessingChanges(final VcsDirtyScope scope) {
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

  boolean processChange(Change change) {
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

  boolean doneProcessingChanges() {
    boolean changesDetected = !Comparing.equal(myChanges, myChangesBeforeUpdate);
    myOutdatedChanges = null;
    myReadChangesCache = null;
    myIsInUpdate = false;
    return changesDetected;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ChangeList list = (ChangeList)o;

    if (myIsDefault != list.myIsDefault) return false;
    if (!myChanges.equals(list.myChanges)) return false;
    if (!myDescription.equals(list.myDescription)) return false;

    return true;
  }

  public int hashCode() {
    return myDescription.hashCode();
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
