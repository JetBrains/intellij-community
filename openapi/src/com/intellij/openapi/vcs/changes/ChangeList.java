package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ChangeList {
  private Collection<Change> myChanges = new ArrayList<Change>();
  private String myDescription;
  private boolean myIsDefault = false;

  public ChangeList(final String description) {
    myDescription = description;
  }

  public Collection<Change> getChanges() {
    return Collections.unmodifiableCollection(myChanges);
  }

  public void addChange(Change change) {
    myChanges.add(change);
  }

  public void removeChange(Change change) {
    myChanges.remove(change);
  }

  public String getDescription() {
    return myDescription;
  }


  public boolean isDefault() {
    return myIsDefault;
  }

  public void setDefault(final boolean isDefault) {
    myIsDefault = isDefault;
  }

  public void updateChangesIn(VcsDirtyScope scope, Collection<Change> newChanges) {
    List<Change> outdatedChanges = new ArrayList<Change>();
    final Collection<Change> currentChanges = new ArrayList<Change>(myChanges);
    for (Change oldBoy : currentChanges) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())) {
        myChanges.remove(oldBoy);
        outdatedChanges.add(oldBoy);
      }
    }

    if (myIsDefault) {
      myChanges.addAll(newChanges);
    }
    else {
      for (Change oldChange : outdatedChanges) {
        for (Change newChange : newChanges) {
          if (changesEqual(oldChange, newChange)) {
            myChanges.add(newChange);
          }
        }
      }
    }
  }

  private static boolean changesEqual(Change c1, Change c2) {
    final ContentRevision br1 = c1.getBeforeRevision();
    final ContentRevision br2 = c2.getBeforeRevision();
    final ContentRevision ar1 = c1.getAfterRevision();
    final ContentRevision ar2 = c2.getAfterRevision();

    FilePath fbr1 = br1 != null ? br1.getFile() : null;
    FilePath fbr2 = br2 != null ? br2.getFile() : null;

    FilePath far1 = ar1 != null ? ar1.getFile() : null;
    FilePath far2 = ar2 != null ? ar2.getFile() : null;

    return Comparing.equal(fbr1, fbr2) && Comparing.equal(far1, far2);
  }
}
