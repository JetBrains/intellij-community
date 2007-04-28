package com.intellij.localvcs.integration;

public interface RevisionTimestampComparator {
  boolean isSuitable(long revisionTimestamp);
}
