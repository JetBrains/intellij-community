package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;

import java.io.IOException;

public class CreateFileChange extends CreateEntryChange<CreateFileChangeNonAppliedState> {
  public CreateFileChange(int id, String path, Content content, long timestamp, boolean isReadOnly) {
    super(id, path);
    getNonAppliedState().myContent = content;
    getNonAppliedState().myTimestamp = timestamp;
    getNonAppliedState().isReadOnly = isReadOnly;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected CreateFileChangeNonAppliedState createNonAppliedState() {
    return new CreateFileChangeNonAppliedState();
  }

  @Override
  protected IdPath doApplyTo(Entry r, StructuralChangeAppliedState newState) {
    String name = getEntryName();
    String parentPath = getEntryParentPath();

    Entry e = new FileEntry(getNonAppliedState().myId,
                            name,
                            getNonAppliedState().myContent,
                            getNonAppliedState().myTimestamp,
                            getNonAppliedState().isReadOnly);

    return addEntry(r, parentPath, e);
  }
}
