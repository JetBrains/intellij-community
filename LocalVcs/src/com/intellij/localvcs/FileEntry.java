package com.intellij.localvcs;

import java.io.IOException;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.DELETED;
import static com.intellij.localvcs.Difference.Kind.MODIFIED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;

public class FileEntry extends Entry {
  // todo change String to ByteArray or something else
  private String myContent;

  public FileEntry(Integer id, String name, String content) {
    super(id, name);
    myContent = content;
  }

  public FileEntry(Stream s) throws IOException {
    super(s);
    myContent = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myContent);
  }

  @Override
  public String getContent() {
    return myContent;
  }

  @Override
  public Entry copy() {
    return new FileEntry(myId, myName, myContent);
  }

  @Override
  public Difference getDifferenceWith(Entry right) {
    FileEntry e = (FileEntry)right;

    boolean modified = !myName.equals(e.myName) ||
                       !myContent.equals(e.myContent);

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
