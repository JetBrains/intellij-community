package com.intellij.history.integration;

public interface RevisionTimestampComparator {
  boolean isSuitable(long revisionTimestamp);
}
