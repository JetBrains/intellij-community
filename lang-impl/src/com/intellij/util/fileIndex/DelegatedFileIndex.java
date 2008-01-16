package com.intellij.util.fileIndex;

import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public interface DelegatedFileIndex<IndexEntry extends FileIndexEntry> extends FileIndex<IndexEntry>{
  void setFileIndexDelegate(@NotNull FileIndexDelegate<IndexEntry> fileIndexDelegate);
}