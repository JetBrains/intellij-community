// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class StaleIndexesChecker {
  private static final Logger LOG = Logger.getInstance(StaleIndexesChecker.class);
  private static final ThreadLocal<Boolean> IS_IN_STALE_IDS_DELETION = new ThreadLocal<>();

  public static boolean isStaleIdDeletion() {
    return IS_IN_STALE_IDS_DELETION.get() == Boolean.TRUE;
  }

  static @NotNull IntSet checkIndexForStaleRecords(@NotNull UpdatableIndex<?, ?, FileContent, ?> index,
                                                   boolean onStartup) throws StorageException {
    if (!ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isEAP()) {
      return IntSets.EMPTY_SET;
    }
    IndexExtension<?, ?, FileContent> extension = index.getExtension();
    IndexId<?, ?> indexId = extension.getName();
    LOG.assertTrue(indexId.equals(StubUpdatingIndex.INDEX_ID));
    LOG.assertTrue(extension instanceof SingleEntryFileBasedIndexExtension);

    Int2ObjectMap<String> staleFiles = new Int2ObjectOpenHashMap<>();
    for (int freeRecord : onStartup ? FSRecords.getRemainFreeRecords() : FSRecords.getNewFreeRecords()) {
      Map<?, ?> dataAsMap = index.getIndexedFileData(freeRecord);
      Object data = ContainerUtil.getFirstItem(dataAsMap.values());
      if (data != null) {
        String name;
        try {
          name = VfsImplUtil.getRecordPath(freeRecord);
        }
        catch (Exception e) {
          name = e.getMessage();
        }
        staleFiles.put(freeRecord, name);
      }
    }

    if (!staleFiles.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() && onStartup) {
        // report it as late as possible, give a chance for test to fail by another reason
        Disposer.register(ApplicationManager.getApplication(), () -> {
          LOG.error(getStaleInputIdsMessage(staleFiles, indexId));
        });
      }
      else {
        LOG.error(getStaleInputIdsMessage(staleFiles, indexId));
      }
    }

    return staleFiles.keySet();
  }

  static void clearStaleIndexes(@NotNull IntSet staleIds) {
    IS_IN_STALE_IDS_DELETION.set(Boolean.TRUE);
    try {
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        for (Integer staleId : staleIds) {
          clearStaleIndexesForId(staleId);
        }
      });
    }
    finally {
      IS_IN_STALE_IDS_DELETION.remove();
    }
  }

  static void clearStaleIndexesForId(int staleInputId) {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    IndexConfiguration state = fileBasedIndex.getRegisteredIndexes().getState();
    for (ID<?, ?> id : state.getIndexIDs()) {
      try {
        UpdatableIndex<?, ?, FileContent, ?> index = state.getIndex(id);
        if (index != null) {
          fileBasedIndex.runUpdateForPersistentData(index.mapInputAndPrepareUpdate(staleInputId, null));
        }
      }
      catch (Exception e) {
        LOG.info(e);
        fileBasedIndex.requestRebuild(id);
      }
    }
  }

  @NotNull
  private static String getStaleInputIdsMessage(Int2ObjectMap<String> staleTrees, IndexId<?, ?> indexId) {
    return "`" + indexId + "` index contains several stale file ids (size = "
           + staleTrees.size()
           + "). Ids & filenames: "
           + StringUtil.first(staleTrees.toString(), 300, true)
           + ".";
  }
}
