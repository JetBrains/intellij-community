package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer id, String name, Long timestamp) {
    super(id, name, timestamp);
  }

  public DirectoryEntry(Stream s) throws IOException {
    super(s);
    myName = s.readString();

    int count = s.readInteger();
    while (count-- > 0) {
      addChild(s.readEntry());
    }
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myName);

    s.writeInteger(myChildren.size());
    for (Entry child : myChildren) {
      s.writeEntry(child);
    }
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    // todo test it
    return getIdPath().appendedWith(id);
  }

  protected String getPathAppendedWith(String name) {
    return Path.appended(getPath(), name);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public void addChild(Entry child) {
    // todo move this check to RootEntry class and clean up tests
    assert doesNotExist(child);

    myChildren.add(child);
    child.setParent(this);
  }

  private boolean doesNotExist(Entry child) {
    // todo replace with existing find/hasEntry methods
    for (Entry e : myChildren) {
      if (e.getName().equals(child.getName())) return false;
    }
    return true;
  }

  @Override
  public void removeChild(Entry child) {
    // todo we should remove child by name!!!
    myChildren.remove(child);
    child.setParent(null);
  }

  @Override
  public List<Entry> getChildren() {
    return myChildren;
  }

  @Override
  public DirectoryEntry copy() {
    DirectoryEntry result = copyEntry();
    for (Entry child : myChildren) {
      result.addChild(child.copy());
    }
    return result;
  }

  protected DirectoryEntry copyEntry() {
    return new DirectoryEntry(myId, myName, myTimestamp); // todo test it!
  }

  @Override
  public Difference getDifferenceWith(Entry right) {
    DirectoryEntry e = (DirectoryEntry)right;

    Difference.Kind kind = myName.equals(e.myName) ? NOT_MODIFIED : MODIFIED;
    Difference result = new Difference(false, kind, this, e);

    addCreatedChildrenDifferences(e, result);
    addDeletedChildrenDifferences(e, result);
    addModifiedChildrenDifference(e, result);

    return result;
  }

  private void addCreatedChildrenDifferences(DirectoryEntry e, Difference d) {
    for (Entry child : e.myChildren) {
      if (findChild(child.getId()) == null) {
        d.addChild(child.asCreatedDifference());
      }
    }
  }

  private void addDeletedChildrenDifferences(DirectoryEntry e, Difference d) {
    for (Entry child : myChildren) {
      if (e.findChild(child.getId()) == null) {
        d.addChild(child.asDeletedDifference());
      }
    }
  }

  private void addModifiedChildrenDifference(DirectoryEntry e, Difference d) {
    for (Entry myChild : myChildren) {
      Entry itsChild = e.findChild(myChild.getId());
      if (itsChild != null) {
        Difference childDiff = myChild.getDifferenceWith(itsChild);
        d.addChild(childDiff);
      }
    }
  }

  @Override
  protected Difference asCreatedDifference() {
    Difference d = new Difference(false, CREATED, null, this);
    for (Entry child : myChildren) {
      d.addChild(child.asCreatedDifference());
    }
    return d;
  }

  @Override
  protected Difference asDeletedDifference() {
    Difference d = new Difference(false, DELETED, this, null);
    for (Entry child : myChildren) {
      d.addChild(child.asDeletedDifference());
    }
    return d;
  }
}
