package com.intellij.history;

public interface RevisionTimestampComparator {
  boolean isSuitable(long revisionTimestamp);
}
