package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;

import java.io.IOException;

public class CreateFileChange extends CreateEntryChange {
  private Content myContent; // transient
  private long myTimestamp; // transient

  public CreateFileChange(int id, String path, Content content, long timestamp) {
    super(id, path);
    myContent = content;
    myTimestamp = timestamp;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    String name = Paths.getNameOf(myPath);
    String parentPath = Paths.getParentOf(myPath);

    Entry e = new FileEntry(myId, name, myContent, myTimestamp);

    return addEntry(r, parentPath, e);
  }
}
