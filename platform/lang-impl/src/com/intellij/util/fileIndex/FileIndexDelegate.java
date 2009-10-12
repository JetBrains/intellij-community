/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
