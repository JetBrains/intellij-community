package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class LocalChangeList implements Cloneable, ChangeList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  private Collection<Change> myChanges = new HashSet<Change>();
  private Collection<Change> myReadChangesCache = null;
  private String myName;
  private String myComment = "";

  private boolean myIsDefault = false;
  private boolean myIsReadOnly = false;
  private Collection<Change> myOutdatedChanges;
  private boolean myIsInUpdate = false;
  private ChangeHashSet myChangesBeforeUpdate;

  public static LocalChangeList createEmptyChangeList(String description) {
    return new LocalChangeList(description);
  }

  private LocalChangeList(final String description) {
    myName = description;
  }

  public synchronized Collection<Change> getChanges() {
    createReadChangesCache();
    if (LOG.isDebugEnabled()) {
      LOG.debug("getChanges() for changelist " + getName() + ": " + myReadChangesCache.size() + " changes");
    }
    return myReadChangesCache;
  }

  private void createReadChangesCache() {
    if (myReadChangesCache == null) {
      final HashSet<Change> changes = new HashSet<Change>(myChanges);
      if (myOutdatedChanges != null) {
        changes.addAll(myOutdatedChanges);
      }
      myReadChangesCache = Collections.unmodifiableCollection(changes);
    }
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
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

  public boolean isReadOnly() {
    return myIsReadOnly;
  }

  public void setReadOnly(final boolean isReadOnly) {
    myIsReadOnly = isReadOnly;
  }

  synchronized void addChange(Change change) {
    if (!myIsInUpdate) myReadChangesCache = null;
    myChanges.add(change);
  }

  synchronized boolean removeChange(Change change) {
    boolean wasRemoved = myChanges.remove(change);
    if (wasRemoved && !myIsInUpdate) {
      myReadChangesCache = null;
    }
    return wasRemoved;
  }

  synchronized void startProcessingChanges(final VcsDirtyScope scope) {
    createReadChangesCache();
    myChangesBeforeUpdate = new ChangeHashSet(myChanges);
    myOutdatedChanges = new ArrayList<Change>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(scope.getProject()).getFileIndex();
    for (Change oldBoy : myChangesBeforeUpdate) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())
        || isIgnoredChange(oldBoy, fileIndex)) {
        myIsInUpdate = true;
        removeChange(oldBoy);
        myOutdatedChanges.add(oldBoy);
      }
    }
    if (isDefault()) {
      myIsInUpdate = true;
    }
  }

  private static boolean isIgnoredChange(final Change change, final ProjectFileIndex fileIndex) {
    boolean beforeRevIgnored = change.getBeforeRevision() == null || isIgnoredRevision(change.getBeforeRevision(), fileIndex);
    boolean afterRevIgnored = change.getAfterRevision() == null || isIgnoredRevision(change.getAfterRevision(), fileIndex);
    return beforeRevIgnored && afterRevIgnored;
  }

  private static boolean isIgnoredRevision(final ContentRevision revision, final ProjectFileIndex fileIndex) {
    VirtualFile vFile = revision.getFile().getVirtualFile();
    return vFile != null && fileIndex.isIgnored(vFile);
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
    boolean changesDetected = (myChanges.size() != myChangesBeforeUpdate.size());

    Change[] newChanges = myChanges.toArray(new Change[myChanges.size()]);
    for (int i = 0; i < newChanges.length; i++) {
      Change oldChange = findOldChange(newChanges[i]);
      if (oldChange != null) {
        newChanges[i] = oldChange;
      }
      else {
        changesDetected = true;
      }
    }

    myChanges = new HashSet<Change>(Arrays.asList(newChanges));
    myOutdatedChanges = null;
    myReadChangesCache = null;
    myIsInUpdate = false;
    return changesDetected;
  }

  @Nullable
  private Change findOldChange(final Change newChange) {
    Change oldChange = myChangesBeforeUpdate.getEqualChange(newChange);
    if (oldChange != null && sameBeforeRevision(oldChange, newChange) &&
        newChange.getFileStatus().equals(oldChange.getFileStatus())) {
      return oldChange;
    }
    return null;
  }

  private static boolean sameBeforeRevision(final Change change1, final Change change2) {
    final ContentRevision b1 = change1.getBeforeRevision();
    final ContentRevision b2 = change2.getBeforeRevision();
    if (b1 != null && b2 != null) {
      final VcsRevisionNumber rn1 = b1.getRevisionNumber();
      final VcsRevisionNumber rn2 = b2.getRevisionNumber();
      return rn1 != VcsRevisionNumber.NULL && rn2 != VcsRevisionNumber.NULL && rn1.compareTo(rn2) == 0;
    }
    return false;
  }

  public synchronized boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LocalChangeList list = (LocalChangeList)o;

    if (myIsDefault != list.myIsDefault) return false;
    if (!myName.equals(list.myName)) return false;
    if (myIsReadOnly != list.myIsReadOnly) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  @Override @NonNls
  public String toString() {
    return "LocalChangeList[name=" + myName + "]";
  }

  public synchronized LocalChangeList clone() {
    try {
      final LocalChangeList copy = (LocalChangeList)super.clone();

      if (myChanges != null) {
        copy.myChanges = new HashSet<Change>(myChanges);
      }

      if (myChangesBeforeUpdate != null) {
        copy.myChangesBeforeUpdate = new ChangeHashSet(myChangesBeforeUpdate);
      }

      if (myOutdatedChanges != null) {
        copy.myOutdatedChanges = new ArrayList<Change>(myOutdatedChanges);
      }

      if (myReadChangesCache != null) {
        copy.myReadChangesCache = new HashSet<Change>(myReadChangesCache);
      }

      return copy;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  private static class ChangeHashSet extends THashSet<Change> {
    public ChangeHashSet(final Collection<? extends Change> changes) {
      super(changes);
    }

    @Nullable Change getEqualChange(Change other) {
      int aIndex = index(other);
      if (aIndex >= 0) return (Change)_set [aIndex];
      return null;
    }
  }
}
