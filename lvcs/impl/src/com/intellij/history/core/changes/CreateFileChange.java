package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;

import java.io.IOException;

public class CreateFileChange extends CreateEntryChange {
  private Content myContent; // transient
  private long myTimestamp; // transient
  private boolean isReadOnly; // transient

  public CreateFileChange(int id, String path, Content content, long timestamp, boolean isReadOnly) {
    super(id, path);
    myContent = content;
    myTimestamp = timestamp;
    this.isReadOnly = isReadOnly;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    String name = getEntryName();
    String parentPath = getEntryParentPath();

    Entry e = new FileEntry(myId, name, myContent, myTimestamp, isReadOnly);

    return addEntry(r, parentPath, e);
  }
}
