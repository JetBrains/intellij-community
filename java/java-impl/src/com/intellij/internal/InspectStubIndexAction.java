// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.search.*;
import com.intellij.psi.stubs.*;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IdIterator;
import com.intellij.util.indexing.StorageException;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class InspectStubIndexAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(InspectStubIndexAction.class);
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    actionPerformed(project);
  }

  public static void actionPerformed(Project project) {
    Collection<Integer> fileIds = FileBasedIndex.getInstance().getAllKeys(StubUpdatingIndex.INDEX_ID, project);

    TIntArrayList staleFileIds = new TIntArrayList();
    List<VirtualFile> staleFiles = new ArrayList<>();
    List<Trinity<VirtualFile, StubIndexKey, Object>> mismatchedKeys = new ArrayList<>();
    for (Integer id : fileIds) {
      VirtualFile vFile;
      try {
        vFile = ManagingFS.getInstance().findFileById(id);
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }
      if (vFile == null) {
        try {
          Map<Integer, SerializedStubTree> staleTree =
            ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID).getIndexedFileData(id);

          if (!staleTree.isEmpty()) {
            staleFileIds.add(id);
          }
        }
        catch (StorageException ex) {
          LOG.error(ex);
        }
        continue;
      }

      if (vFile.isInLocalFileSystem()) {
        File ioFile = VfsUtilCore.virtualToIoFile(vFile);
        if (!ioFile.exists()) {
          staleFiles.add(vFile);
          continue;
        }
      }

      Map<Integer, SerializedStubTree> data = FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, vFile, project);
      if (data.isEmpty()) {
        continue;
      }
      if (!((FileBasedIndexImpl)FileBasedIndex.getInstance()).projectIndexableFiles(project).containsFileId(id)) {
        continue;
      }
      try {
        SerializedStubTree tree = data.values().iterator().next();
        for (Map.Entry<StubIndexKey, Map<Object, StubIdList>> entry : tree.readStubIndicesValueMap().entrySet()) {
          StubIndexKey stubIndexKey = entry.getKey();
          Set<Object> keys = entry.getValue().keySet();

          for (Object key : keys) {
            boolean found = false;
            IdIterator ids = StubIndex.getInstance().getContainingIds(stubIndexKey, key, project, GlobalSearchScope.allScope(project));
            while (ids.hasNext()) {
              if (ids.next() == id) {
                found = true;
                break;
              }
            }

            if (!found) {
              mismatchedKeys.add(Trinity.create(vFile, stubIndexKey, key));
            }
          }
        }
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }

    LOG.info("Stub index analysis is finished for " + fileIds.size() + " records.");
    if (!staleFileIds.isEmpty()) {
      LOG.info("Stale file ids: " + staleFileIds);
    }
    if (!staleFiles.isEmpty()) {
      LOG.info("Stale files: " + staleFiles);
    }
    if (!mismatchedKeys.isEmpty()) {
      for (Trinity<VirtualFile, StubIndexKey, Object> key : mismatchedKeys) {
        LOG.info("mismatched key file = " + key.getFirst() + " id = " + ((VirtualFileWithId)key.getFirst()).getId() + " index = " + key.getSecond().getName() + " index key = " + key.getThird());
      }
    }
  }
}
