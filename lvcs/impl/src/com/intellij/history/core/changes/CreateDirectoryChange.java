package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.IdPath;

import java.io.IOException;

public class CreateDirectoryChange extends CreateEntryChange<CreateEntryChangeNonAppliedState> {
  public CreateDirectoryChange(int id, String path) {
    super(id, path);
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r, StructuralChangeAppliedState newState) {
    // todo messsssss!!!! should introduce createRoot method instead?
    // todo and simplify addEntry method too?
    String name = getEntryName();
    String parentPath = getEntryParentPath();

    if (parentPath == null || !r.hasEntry(parentPath)) { // is it supposed to be a root?
      parentPath = null;
      name = getPath();
    }

    DirectoryEntry e = new DirectoryEntry(getNonAppliedState().myId, name);
    return addEntry(r, parentPath, e);
  }
}
