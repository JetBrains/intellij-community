package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer id, String name) {
    super(id, name);
  }

  public DirectoryEntry(Stream s) throws IOException {
    super(s);

    int count = s.readInteger();
    while (count-- > 0) {
      myChildren.add(s.readEntry());
    }
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);

    s.writeInteger(myChildren.size());
    for (Entry child : myChildren) {
      s.writeEntry(child);
    }
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
  protected Entry findEntry(Matcher m) {
    if (m.matches(this)) return this;

    for (Entry child : myChildren) {
      Entry result = child.findEntry(m);
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public Entry copy() {
    DirectoryEntry result = copyEntry();
    for (Entry child : myChildren) {
      result.addChild(child.copy());
    }
    return result;
  }

  protected DirectoryEntry copyEntry() {
    return new DirectoryEntry(myId, myName);
  }
}
