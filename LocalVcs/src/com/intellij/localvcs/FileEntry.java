package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;

import java.io.IOException;

public class FileEntry extends Entry {
  // todo change String to ByteArray or something else
  private FileEntryState myState;

  public FileEntry(Integer id, String name, String content, Long timestamp) {
    super(id, timestamp);
    myState = new FileEntryState(name, content);
  }

  public FileEntry(Stream s) throws IOException {
    super(s);
    myState = new FileEntryState(s.readString(), s.readString());
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myState.getName());
    s.writeString(myState.getContent());
  }

  @Override
  public String getName() {
    return myState.getName();
  }

  @Override
  public String getContent() {
    return myState.getContent();
  }

  @Override
  public FileEntry copy() {
    // todo create constructor with FileEntryState parameter
    return new FileEntry(myId, getName(), getContent(), null);
  }

  public Entry renamed(String newName, final Long timestamp) {
    return new FileEntry(myId, newName, getContent(), timestamp);
  }

  @Override
  public Entry withContent(String newContent, Long timestamp) {
    return new FileEntry(myId, getName(), newContent, timestamp);
  }

  @Override
  public Difference getDifferenceWith(Entry e) {
    boolean modified = !getName().equals(e.getName()) || !getContent().equals(e.getContent());
    return new Difference(true, modified ? MODIFIED : NOT_MODIFIED, this, e);
  }

  @Override
  protected Difference asCreatedDifference() {
    return new Difference(true, CREATED, null, this);
  }

  @Override
  protected Difference asDeletedDifference() {
    return new Difference(true, DELETED, this, null);
  }
}
