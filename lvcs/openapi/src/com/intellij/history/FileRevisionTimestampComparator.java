package com.intellij.history;

public interface FileRevisionTimestampComparator {
  boolean isSuitable(final long fileTimestamp, long revisionTimestamp);
}
