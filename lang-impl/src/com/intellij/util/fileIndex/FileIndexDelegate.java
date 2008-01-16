package com.intellij.util.fileIndex;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author mike
 */
public interface FileIndexDelegate<IndexEntry extends FileIndexEntry> {
  IndexEntry createIndexEntry(final DataInputStream input) throws IOException;
  /*
  void read(final DataInputStream stream) throws IOException;
  void write(final DataInputStream stream) throws IOException;
  */

  String getLoadingIndicesMessage();

  String getBuildingIndicesMessage(final boolean formatChanged);

  boolean belongs(final VirtualFile file);

  byte getCurrentVersion();

  @NonNls
  String getCachesDirName();

  void queueEntryUpdate(final VirtualFile file);

  void doUpdateIndexEntry(final VirtualFile file);

  void afterInitialize();
  void beforeDispose();

  void onEntryAdded(final String url, final IndexEntry indexEntry);
  void onEntryRemoved(final String url, final IndexEntry indexEntry);
  void clear();
}
