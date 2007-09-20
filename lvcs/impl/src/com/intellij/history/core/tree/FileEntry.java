package com.intellij.history.core.tree;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;

import java.io.IOException;
import java.util.List;

public class FileEntry extends Entry {
  private long myTimestamp;
  private boolean isReadOnly;
  private Content myContent;

  public FileEntry(int id, String name, Content content, long timestamp, boolean isReadOnly) {
    super(id, name);
    myTimestamp = timestamp;
    this.isReadOnly = isReadOnly;
    myContent = content;
  }

  public FileEntry(Stream s) throws IOException {
    super(s);
    myTimestamp = s.readLong();
    isReadOnly = s.readBoolean();
    myContent = s.readContent();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeLong(myTimestamp);
    s.writeBoolean(isReadOnly);
    s.writeContent(myContent);
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public boolean isReadOnly() {
    return isReadOnly;
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    this.isReadOnly = isReadOnly;
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
    return new FileEntry(myId, myName, myContent, myTimestamp, isReadOnly);
  }

  @Override
  public void changeContent(Content newContent, long newTimestamp) {
    myContent = newContent;
    myTimestamp = newTimestamp;
  }

  @Override
  public void collectDifferencesWith(Entry e, List<Difference> result) {
    if (getPath().equals(e.getPath())
        && myContent.equals(e.getContent())
        && isReadOnly == e.isReadOnly()) return;
    
    result.add(new Difference(true, this, e));
  }

  @Override
  protected void collectCreatedDifferences(List<Difference> result) {
    result.add(new Difference(true, null, this));
  }

  @Override
  protected void collectDeletedDifferences(List<Difference> result) {
    result.add(new Difference(true, this, null));
  }
}
