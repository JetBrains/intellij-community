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

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public abstract class AbstractFileIndex<IndexEntry extends FileIndexEntry> implements FileIndex<IndexEntry> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.fileIndex.AbstractFileIndex");
  private final Map<String, IndexEntry> myFileUrl2IndexEntry = new HashMap<String, IndexEntry>();
  private final ProjectFileIndex myProjectFileIndex;
  private final Project myProject;
  private FileIndexCacheUpdater myRootsChangeCacheUpdater;
  private final StartupManagerEx myStartupManager;
  private FileIndexRefreshCacheUpdater myRefreshCacheUpdater;
  private final Object myIndexLock = new Object();

  protected AbstractFileIndex(final Project project) {
    myProject = project;
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myStartupManager = StartupManagerEx.getInstanceEx(project);
  }

  protected abstract IndexEntry createIndexEntry(DataInputStream input) throws IOException;


  protected abstract String getLoadingIndicesMessage();

  public abstract boolean belongs(VirtualFile file);


  public abstract byte getCurrentVersion();

  @NonNls
  public abstract String getCachesDirName();

  public abstract void queueEntryUpdate(final VirtualFile file);

  protected abstract void doUpdateIndexEntry(final VirtualFile file);

  public ProjectFileIndex getProjectFileIndex() {
    return myProjectFileIndex;
  }

  protected File getCacheLocation(final String dirName) {
    final String cacheFileName = myProject.getName() + "." + myProject.getLocationHash();
    return new File(PathManager.getSystemPath() + File.separator + dirName + File.separator + cacheFileName);
  }

  public final void updateIndexEntry(final VirtualFile file) {
    if (!myStartupManager.startupActivityPassed() || myProjectFileIndex.isIgnored(file)) {
      return;
    }
    
    doUpdateIndexEntry(file);
  }

  public final void removeIndexEntry(final VirtualFile file) {
    if (myProjectFileIndex.isIgnored(file)) {
      return;
    }

    removeIndexEntry(file.getUrl());
  }


  protected void onEntryAdded(String url, IndexEntry entry) {
  }

  protected void onEntryRemoved(String url, IndexEntry entry) {
  }

  private void saveCache() {
    final File cacheFile = getCacheLocation(getCachesDirName());
    FileUtil.createParentDirs(cacheFile);
    DataOutputStream output = null;
    try {
      output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)));
      output.writeByte(getCurrentVersion());
      writeHeader(output);
      synchronized (myIndexLock) {
        output.writeInt(myFileUrl2IndexEntry.size());
        for (final Map.Entry<String, IndexEntry> entry : myFileUrl2IndexEntry.entrySet()) {
          output.writeUTF(entry.getKey());
          entry.getValue().write(output);
        }
      }
      output.close();
    }
    catch (IOException e) {
      LOG.debug(e);
      if (output != null) {
        try {
          output.close();
          output = null;
        }
        catch (IOException e1) {
        }
      }
      cacheFile.delete();
    }
    finally {
      if (output != null) {
        try {
          output.close();
        }
        catch (IOException e1) {
        }
      }
    }
  }

  protected void readHeader(DataInputStream input) throws IOException {
  }

  protected void writeHeader(final DataOutputStream output) throws IOException {
  }

  private boolean loadCache() {
    final File cacheFile = getCacheLocation(getCachesDirName());
    if (!cacheFile.exists()) return false;
    clearMaps();

    DataInputStream input = null;
    final ProgressIndicator indicator = getProgressIndicator();
    try {
      input = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)));
      int version = input.readByte();
      if (version != getCurrentVersion()) {
        return false;
      }

      if (indicator != null) {
        indicator.pushState();
        indicator.setText(getLoadingIndicesMessage());
      }

      readHeader(input);

      int size = input.readInt();
      for (int i = 0; i < size; i++) {
        if (indicator != null) {
          indicator.setFraction(((double)i) / size);
        }
        final String url = input.readUTF();
        putIndexEntry(url, createIndexEntry(input));
      }
      if (indicator != null) {
        indicator.popState();
      }

      input.close();
      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
    } finally {
      if (input != null) {
        try {
          input.close();
        }
        catch (IOException e1) {
        }
      }
    }
    return false;
  }

  public final void putIndexEntry(final String url, final IndexEntry entry) {
    synchronized (myIndexLock) {
      myFileUrl2IndexEntry.put(url, entry);
    }
    onEntryAdded(url, entry);
  }

  public final IndexEntry getIndexEntry(final String url) {
    synchronized (myIndexLock) {
      return myFileUrl2IndexEntry.get(url);
    }
  }

  @Nullable
  public final IndexEntry removeIndexEntry(final String url) {
    final IndexEntry entry;
    synchronized (myIndexLock) {
      entry = myFileUrl2IndexEntry.remove(url);
    }
    if (entry != null) {
      onEntryRemoved(url, entry);
    }
    return entry;
  }

  protected void clearMaps() {
    synchronized (myIndexLock) {
      myFileUrl2IndexEntry.clear();
    }
  }

  public void initialize() {
    final StartupManager sm = StartupManager.getInstance(myProject);
    final Runnable startupRunnable = new Runnable() {
      public void run() {
        loadCache();
        sm.registerCacheUpdater(new FileIndexCacheUpdater(true, getFileTypesToRefresh()));
        myRootsChangeCacheUpdater = new FileIndexCacheUpdater(false, null);
        ProjectRootManagerEx.getInstanceEx(myProject).registerRootsChangeUpdater(myRootsChangeCacheUpdater);
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          myRefreshCacheUpdater = new FileIndexRefreshCacheUpdater(myProject, AbstractFileIndex.this);
        }
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myRefreshCacheUpdater = new FileIndexRefreshCacheUpdater(myProject, this);
      startupRunnable.run();
    }
    else {
      sm.registerStartupActivity(startupRunnable);
    }
  }

  @Nullable
  private static ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
  }

  public void dispose() {
    if (myRefreshCacheUpdater != null) {
      Disposer.dispose(myRefreshCacheUpdater);
    }
    if (myRootsChangeCacheUpdater != null) {
      ProjectRootManagerEx.getInstanceEx(myProject).unregisterRootsChangeUpdater(myRootsChangeCacheUpdater);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      getCacheLocation(getCachesDirName()).delete();
    }
    else {
      saveCache();
    }
    clearMaps();
  }

  @Nullable
  protected Set<FileType> getFileTypesToRefresh() {
    return null;
  }

  private VirtualFile[] queryNeededFiles(final boolean includeChangedFiles, @Nullable Set<FileType> fileTypesToRefresh) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    myProjectFileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (belongs(fileOrDir)) {
          files.add(fileOrDir);
        }
        return true;
      }
    });

    List<VirtualFile> toUpdate = new ArrayList<VirtualFile>();
    Set<String> toRemove;
    synchronized (myIndexLock) {
      toRemove = new THashSet<String>(myFileUrl2IndexEntry.keySet());

      final int size = files.size();
      for (int i = 0; i < size; i++) {
        final VirtualFile file = files.get(i);
        final String url = file.getUrl();
        final IndexEntry entry = myFileUrl2IndexEntry.get(url);
        toRemove.remove(url);
        if (entry == null
            || includeChangedFiles && entry.getTimeStamp() != file.getTimeStamp()
            || fileTypesToRefresh != null && fileTypesToRefresh.contains(file.getFileType())) {
          toUpdate.add(file);
        }
      }
    }

    for (String url : toRemove) {
      removeIndexEntry(url);
    }

    return VfsUtil.toVirtualFileArray(toUpdate);
  }

  private class FileIndexCacheUpdater implements CacheUpdater {
    private final boolean myIncludeChangedFiles;
    private final Set<FileType> myFileTypesToRefresh;

    public FileIndexCacheUpdater(boolean includeChangedFiles, Set<FileType> fileTypesToRefresh) {
      myIncludeChangedFiles = includeChangedFiles;
      myFileTypesToRefresh = fileTypesToRefresh;
    }

    public VirtualFile[] queryNeededFiles() {
      return AbstractFileIndex.this.queryNeededFiles(myIncludeChangedFiles, myFileTypesToRefresh);
    }

    public int getNumberOfPendingUpdateJobs() {
      return 0;
    }

    public void processFile(FileContent fileContent) {
      updateIndexEntry(fileContent.getVirtualFile());
    }

    public void updatingDone() {
    }

    public void canceled() {
    }

  }
}
