// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.REUSE_DELETED_FILE_IDS;

public final class StaleIndexesChecker {
  private static final Logger LOG = Logger.getInstance(StaleIndexesChecker.class);
  private static final ThreadLocal<Boolean> IS_IN_STALE_IDS_DELETION = new ThreadLocal<>();

  public static boolean isStaleIdDeletion() {
    return IS_IN_STALE_IDS_DELETION.get() == Boolean.TRUE;
  }

  public static boolean shouldCheckStaleIndexesOnStartup() {
    return REUSE_DELETED_FILE_IDS && (ApplicationManager.getApplication().isInternal() || ApplicationManager.getApplication().isEAP());
  }

  static @NotNull IntSet checkIndexForStaleRecords(@NotNull UpdatableIndex<?, ?, FileContent, ?> index,
                                                   IntSet knownStaleIds,
                                                   boolean onStartup) throws StorageException {
    IndexExtension<?, ?, FileContent> extension = index.getExtension();
    IndexId<?, ?> indexId = extension.getName();
    LOG.assertTrue(indexId.equals(StubUpdatingIndex.INDEX_ID));
    LOG.assertTrue(extension instanceof SingleEntryFileBasedIndexExtension);

    Int2ObjectMap<String> staleFiles = new Int2ObjectOpenHashMap<>();
    for (int freeRecord : onStartup ? FSRecords.getRemainFreeRecords() : FSRecords.getNewFreeRecords()) {
      if (knownStaleIds.contains(freeRecord)) {
        continue;
      }
      Map<?, ?> dataAsMap = index.getIndexedFileData(freeRecord);
      Object data = ContainerUtil.getFirstItem(dataAsMap.values());
      if (data != null) {
        String name;
        name = getStaleRecordOrExceptionMessage(freeRecord);
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

  static String getStaleRecordOrExceptionMessage(int record) {
    try {
      return getRecordPath(record);
    }
    catch (Exception e) {
      return e.getMessage();
    }
  }

  private static String getRecordPath(int record) {
    FSRecordsImpl vfs = FSRecords.getInstance();
    StringBuilder name = new StringBuilder(vfs.getName(record));
    int parent = vfs.getParent(record);
    while (parent > 0) {
      name.insert(0, vfs.getName(parent) + "/");
      parent = vfs.getParent(parent);
    }
    return name.toString();
  }

  static void clearStaleIndexes(@NotNull IntSet staleIds) {
    IS_IN_STALE_IDS_DELETION.set(Boolean.TRUE);
    boolean unitTest = ApplicationManager.getApplication().isUnitTestMode();
    try {
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        final int maxLogCount = (unitTest || FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES || LOG.isDebugEnabled()) ? Integer.MAX_VALUE : 10;
        int loggedCount = 0;
        for (int staleId : staleIds) {
          if (loggedCount < maxLogCount) {
            LOG.info("clearing stale id = " + staleId + ", path =  " + getRecordPath(staleId));
          }
          else if (loggedCount == maxLogCount) {
            LOG.info(
              "clearing more items (not logged due to logging limit). Use -Didea.trace.stub.index.update=true, " +
              "or enable debug log for: #" + StaleIndexesChecker.class.getName()
            );
          }
          loggedCount++;
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
    Collection<ID<?, ?>> indexIds = fileBasedIndex.getRegisteredIndexes().getState().getIndexIDs();
    fileBasedIndex.removeFileDataFromIndices(indexIds, staleInputId, null);
  }

  private static @NotNull String getStaleInputIdsMessage(Int2ObjectMap<String> staleTrees, IndexId<?, ?> indexId) {
    return "`" + indexId + "` index contains several stale file ids (size = "
           + staleTrees.size()
           + "). Ids & paths: "
           + StringUtil.first(staleTrees.toString(), 300, true)
           + ".";
  }
}
