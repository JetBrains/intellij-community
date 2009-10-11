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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author mike
 */
public class DelegatedFileIndexImpl<IndexEntry extends FileIndexEntry> extends AbstractFileIndex<IndexEntry> implements DelegatedFileIndex<IndexEntry> {
  private FileIndexDelegate<IndexEntry> myFileIndexDelegate;

  public DelegatedFileIndexImpl(final Project project) {
    super(project);
  }

  public DelegatedFileIndexImpl(final Project project, @NotNull final FileIndexDelegate<IndexEntry> fileIndexDelegate) {
    this(project);
    myFileIndexDelegate = fileIndexDelegate;
  }

  public void setFileIndexDelegate(@NotNull final FileIndexDelegate<IndexEntry> fileIndexDelegate) {
    assert myFileIndexDelegate == null;
    myFileIndexDelegate = fileIndexDelegate;
  }

  protected IndexEntry createIndexEntry(final DataInputStream input) throws IOException {
    return myFileIndexDelegate.createIndexEntry(input);
  }

  protected String getLoadingIndicesMessage() {
    return myFileIndexDelegate.getLoadingIndicesMessage();
  }

  protected String getBuildingIndicesMessage(final boolean formatChanged) {
    return myFileIndexDelegate.getBuildingIndicesMessage(formatChanged);
  }

  public boolean belongs(final VirtualFile file) {
    return myFileIndexDelegate.belongs(file);
  }

  public byte getCurrentVersion() {
    return myFileIndexDelegate.getCurrentVersion();
  }

  public String getCachesDirName() {
    return myFileIndexDelegate.getCachesDirName();
  }

  public void queueEntryUpdate(final VirtualFile file) {
    myFileIndexDelegate.queueEntryUpdate(file);
  }

  protected void doUpdateIndexEntry(final VirtualFile file) {
    myFileIndexDelegate.doUpdateIndexEntry(file);
  }

  public void initialize() {
    super.initialize();
    myFileIndexDelegate.afterInitialize();
  }

  public void dispose() {
    myFileIndexDelegate.beforeDispose();
    super.dispose();
  }

  protected void clearMaps() {
    super.clearMaps();
    myFileIndexDelegate.clear();
  }

  protected void onEntryAdded(final String url, final IndexEntry indexEntry) {
    super.onEntryAdded(url, indexEntry);
    myFileIndexDelegate.onEntryAdded(url, indexEntry);
  }

  protected void onEntryRemoved(final String url, final IndexEntry indexEntry) {
    super.onEntryRemoved(url, indexEntry);
    myFileIndexDelegate.onEntryRemoved(url, indexEntry);
  }
}
