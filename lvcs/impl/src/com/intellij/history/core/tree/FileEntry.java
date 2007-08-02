package com.intellij.history.core.tree;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;

import java.io.IOException;
import java.util.List;

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
  public void collectDifferencesWith(Entry e, List<Difference> result) {
    if (myName.equals(e.getName()) && myContent.equals(e.getContent())) return;
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
