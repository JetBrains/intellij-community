package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Content;

public class ContentChangeAppliedState extends StructuralChangeAppliedState {
  public Content myOldContent;
  public long myOldTimestamp;
}
