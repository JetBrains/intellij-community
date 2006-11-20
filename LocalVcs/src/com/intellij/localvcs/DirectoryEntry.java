package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.DELETED;
import static com.intellij.localvcs.Difference.Kind.MODIFIED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;

public class DirectoryEntry extends Entry {
  protected String myName;
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer id, String name) {
    super(id);
    myName = name;
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

  public String getName() {
    return myName;
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    return getIdPath().appendedWith(id);
  }

  protected Path getPathAppendedWith(String name) {
    return getPath().appendedWith(name);
  }

  @Override
  public Boolean isDirectory() {
    return true;
  }

  @Override
  public void addChild(Entry child) {
    // todo move this check to RootEntry class and clean up tests
    checkThatEntryDoesNotExists(child);
    myChildren.add(child);
    child.setParent(this);
  }

  private void checkThatEntryDoesNotExists(Entry child) {
    // todo replace with existing find/hasEntry methods
    for (Entry e : myChildren) {
      if (e.getName().equals(child.getName())) throw new LocalVcsException();
    }
  }

  @Override
  public void removeChild(Entry child) {
    // todo we shoult remove child by name!!!
    for (int i = 0; i < myChildren.size(); i++) {
      if (myChildren.get(i) == child) {
        myChildren.remove(i);
        break;
      }
    }
    child.setParent(null);
  }

  @Override
  public List<Entry> getChildren() {
    return myChildren;
  }

  @Override
  protected Entry getChild(Integer id) {
    for (Entry child : myChildren) {
      if (child.getId().equals(id)) return child;
    }
    return null;
  }

  @Override
  protected Entry findEntry(Matcher m) {
    if (m.matches(this)) return this;

    for (Entry child : myChildren) {
      Entry result = child.findEntry(m);
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public DirectoryEntry copy() {
    DirectoryEntry result = copyEntry();
    for (Entry child : myChildren) {
      result.addChild(child.copy());
    }
    return result;
  }

  public Entry renamed(String newName) {
    DirectoryEntry result = copy();
    result.myName = newName;
    return result;
  }

  protected DirectoryEntry copyEntry() {
    return new DirectoryEntry(myId, myName);
  }

  @Override
  public Difference getDifferenceWith(Entry right) {
    DirectoryEntry e = (DirectoryEntry)right;

    Difference.Kind kind = myName.equals(e.myName) ? NOT_MODIFIED : MODIFIED;
    Difference result = new Difference(false, kind, this, e);

    addCreatedChildren(e, result);
    addDeletedChildren(e, result);
    addModifiedChildren(e, result);

    return result;
  }

  private void addCreatedChildren(DirectoryEntry e, Difference d) {
    for (Entry child : e.myChildren) {
      if (getChild(child.getId()) == null) {
        d.addChild(child.asCreatedDifference());
      }
    }
  }

  private void addDeletedChildren(DirectoryEntry e, Difference d) {
    for (Entry child : myChildren) {
      if (e.getChild(child.getId()) == null) {
        d.addChild(child.asDeletedDifference());
      }
    }
  }

  private void addModifiedChildren(DirectoryEntry e, Difference d) {
    for (Entry myChild : myChildren) {
      Entry itsChild = e.getChild(myChild.getId());
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
