package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer objectId, String name) {
    super(objectId, name);
  }

  public DirectoryEntry(Stream s) throws IOException {
    super(s);
    readChildren(s);
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    writeChildren(s);
  }

  protected void readChildren(Stream s) throws IOException {
    int count = s.readInteger();
    for (int i = 0; i < count; i++) {
      myChildren.add(s.readEntry());
    }
  }

  protected void writeChildren(Stream s) throws IOException {
    s.writeInteger(myChildren.size());
    for (Entry child : myChildren) {
      s.writeEntry(child);
    }
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
    // todo we shoult removeChild by name!!!
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
    // todo a bit messy
    Entry result = super.findEntry(m);
    if (result != null) return result;

    for (Entry child : myChildren) {
      result = child.findEntry(m);
      if (result != null) return result;
    }

    return null;
  }

  @Override
  public Entry copy() {
    Entry result = copyEntry();
    for (Entry child : myChildren) {
      result.addChild(child.copy());
    }
    return result;
  }

  protected Entry copyEntry() {
    return new DirectoryEntry(myObjectId, myName);
  }
}
