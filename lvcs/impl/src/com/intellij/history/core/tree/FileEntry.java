package com.intellij.history.core.tree;

import com.intellij.history.core.revisions.Difference;
import static com.intellij.history.core.revisions.Difference.Kind.*;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;

import java.io.IOException;

public class FileEntry extends Entry {
  private long myTimestamp;
  private Content myContent;

  public FileEntry(int id, String name, Content content, long timestamp) {
    super(id, name);
    myTimestamp = timestamp;
    myContent = content;
  }

  public FileEntry(Stream s) throws IOException {
    super(s);
    myTimestamp = s.readLong();
    myContent = s.readContent();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeLong(myTimestamp);
    s.writeContent(myContent);
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public Content getContent() {
    return myContent;
  }

  @Override
  public boolean hasUnavailableContent() {
    return !myContent.isAvailable();
  }

  @Override
  public FileEntry copy() {
    return new FileEntry(myId, myName, myContent, myTimestamp);
  }

  @Override
  public void changeContent(Content newContent, long newTimestamp) {
    myContent = newContent;
    myTimestamp = newTimestamp;
  }

  @Override
  public Difference getDifferenceWith(Entry e) {
    boolean modified = !myName.equals(e.getName()) || !myContent.equals(e.getContent());
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
