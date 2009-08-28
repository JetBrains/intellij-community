package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Content;

public class CreateFileChangeNonAppliedState extends CreateEntryChangeNonAppliedState {
  public Content myContent;
  public long myTimestamp;
  public boolean isReadOnly;
}
