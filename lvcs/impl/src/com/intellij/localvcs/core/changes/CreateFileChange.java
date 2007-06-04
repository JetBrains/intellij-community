package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.FileEntry;

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
