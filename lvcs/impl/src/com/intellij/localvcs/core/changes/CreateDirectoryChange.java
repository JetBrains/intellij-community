package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.DirectoryEntry;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;

public class CreateDirectoryChange extends CreateEntryChange {
  private int myId; // transient

  public CreateDirectoryChange(int id, String path) {
    super(id, path);
    myId = id;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    // todo messsssss!!!! should introduce createRoot method instead?
    // todo and simplify addEntry method too?
    String name = Paths.getNameOf(myPath);
    String parentPath = Paths.getParentOf(myPath);

    if (parentPath == null || !r.hasEntry(parentPath)) { // is it supposed to be a root?
      parentPath = null;
      name = myPath;
    }

    DirectoryEntry e = new DirectoryEntry(myId, name);
    return addEntry(r, parentPath, e);
  }
}
