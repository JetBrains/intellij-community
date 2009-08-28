package com.intellij.util.fileIndex;

import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public interface FileIndex<IndexEntry extends FileIndexEntry> {
  void initialize();

  void dispose();

  void putIndexEntry(String url, IndexEntry entry);

  IndexEntry getIndexEntry(String url);

  @Nullable
  IndexEntry removeIndexEntry(String url);
}
