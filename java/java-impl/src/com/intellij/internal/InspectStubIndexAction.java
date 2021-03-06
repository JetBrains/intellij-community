// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.*;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IdIterator;
import com.intellij.util.indexing.StorageException;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class InspectStubIndexAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(InspectStubIndexAction.class);
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ProgressManager.getInstance().run(new Task.Modal(project, "Inspecting Stub Index", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ReadAction.run(() -> {
          actionPerformed(project, indicator);
        });
      }
    });
  }

  public static void actionPerformed(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    List<Integer> fileIds = new ArrayList<>(FileBasedIndex.getInstance().getAllKeys(StubUpdatingIndex.INDEX_ID, project));

    TIntArrayList staleFileIds = new TIntArrayList();
    List<VirtualFile> staleFiles = new ArrayList<>();
    List<Trinity<VirtualFile, StubIndexKey, Object>> mismatchedKeys = new ArrayList<>();
    indicator.setIndeterminate(false);
    AtomicInteger counter = new AtomicInteger();

    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(fileIds, indicator, id -> {
      indicator.setFraction(((double) counter.incrementAndGet()) / fileIds.size());
      if (!((FileBasedIndexImpl)FileBasedIndex.getInstance()).projectIndexableFiles(project).containsFileId(id)) {
        return true;
      }
      VirtualFile vFile;
      try {
        vFile = ManagingFS.getInstance().findFileById(id);
      }
      catch (Exception e) {
        LOG.error(e);
        return true;
      }
      if (vFile == null) {
        try {
          Map<Integer, SerializedStubTree> staleTree =
            ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID).getIndexedFileData(id);

          if (!staleTree.isEmpty()) {
            synchronized (staleFileIds) {
              staleFileIds.add(id);
            }
          }
        }
        catch (StorageException ex) {
          LOG.error(ex);
        }
        return true;
      }

      if (vFile.isInLocalFileSystem()) {
        File ioFile = VfsUtilCore.virtualToIoFile(vFile);
        if (!ioFile.exists()) {
          synchronized (staleFiles) {
            staleFiles.add(vFile);
          }
          return true;
        }
      }

      SerializedStubTree tree = FileBasedIndex.getInstance().getSingleEntryIndexData(StubUpdatingIndex.INDEX_ID, vFile, project);
      if (tree == null) {
        return true;
      }

      try {
        for (Map.Entry<StubIndexKey<?, ?>, Map<Object, StubIdList>> entry : tree.getStubIndicesValueMap().entrySet()) {
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
              synchronized (mismatchedKeys) {
                mismatchedKeys.add(Trinity.create(vFile, stubIndexKey, key));
              }
            }
          }
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
      return true;
    });

    LOG.info("Stub index analysis is finished for " + fileIds.size() + " records.");
    synchronized (staleFileIds) {
      if (!staleFileIds.isEmpty()) {
        LOG.info("Stale file ids: " + staleFileIds);
      }
    }
    synchronized (staleFiles) {
      if (!staleFiles.isEmpty()) {
        LOG.info("Stale files: " + staleFiles);
      }
    }
    synchronized (mismatchedKeys) {
      if (!mismatchedKeys.isEmpty()) {
        for (Trinity<VirtualFile, StubIndexKey, Object> key : mismatchedKeys) {
          LOG.info("mismatched key file = " + key.getFirst() + " id = " + ((VirtualFileWithId)key.getFirst()).getId() + " index = " + key.getSecond().getName() + " index key = " + key.getThird());
        }
      }
    }
  }
}
