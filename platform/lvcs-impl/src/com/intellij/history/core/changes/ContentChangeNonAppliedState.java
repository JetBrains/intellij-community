package com.intellij.history.core.changes;

import com.intellij.history.core.storage.Content;

public class ContentChangeNonAppliedState extends StructuralChangeNonAppliedState {
  public Content myNewContent;
  public long myNewTimestamp;
}
