// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.DirectInputDataDiffBuilder;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.UpdatedEntryProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class StubCumulativeInputDiffBuilder extends DirectInputDataDiffBuilder<Integer, SerializedStubTree> {
  private static final Logger LOG = Logger.getInstance(SerializedStubTree.class);
  private final @Nullable SerializedStubTree myCurrentTree;

  StubCumulativeInputDiffBuilder(int inputId, @Nullable SerializedStubTree currentTree) {
    super(inputId);
    myCurrentTree = currentTree;
  }

  @Override
  public boolean differentiate(@NotNull Map<Integer, SerializedStubTree> newData,
                               @NotNull UpdatedEntryProcessor<? super Integer, ? super SerializedStubTree> changesProcessor) throws StorageException {
    return differentiate(newData, changesProcessor, false);
  }

  /**
   * @param dryRun if true, won't update the stub indices
   */
  public boolean differentiate(@NotNull Map<Integer, SerializedStubTree> newData,
                               @NotNull UpdatedEntryProcessor<? super Integer, ? super SerializedStubTree> changesProcessor,
                               boolean dryRun) throws StorageException {
    if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
      LOG.info((dryRun ? "[dry run]" : "") + "differentiate: inputId=" + myInputId +
               ",newData.isEmpty=" + newData.isEmpty() +
               ", myCurrentTree is " + ((myCurrentTree == null) ? "null" : "not null"));
    }

    if (!newData.isEmpty()) {
      SerializedStubTree newSerializedStubTree = newData.values().iterator().next();
      if (myCurrentTree != null) {
        if (treesAreEqual(newSerializedStubTree, myCurrentTree)) {
          if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
            LOG.info((dryRun ? "[dry run]" : "") + "equal trees: inputId=" + myInputId +
                     ",myTreeLen=" + myCurrentTree.myTreeByteLength +
                     ",myStubLen=" + myCurrentTree.myIndexedStubByteLength +
                     ",newTreeLen=" + newSerializedStubTree.myTreeByteLength +
                     ",newStubLen=" + newSerializedStubTree.myIndexedStubByteLength);
          }
          return false;
        }
        else {
          if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
            LOG.info((dryRun ? "[dry run]" : "") + "different trees: inputId=" + myInputId +
                     ",myStubLen=" + myCurrentTree.myIndexedStubByteLength +
                     ",newStubLen=" + newSerializedStubTree.myIndexedStubByteLength);
          }
        }
        changesProcessor.removed(myInputId, myInputId);
      }
      changesProcessor.added(myInputId, newSerializedStubTree, myInputId);
      if (!dryRun) updateStubIndices(newSerializedStubTree);
    }
    else {
      if (myCurrentTree == null) {
        if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
          LOG.info((dryRun ? "[dry run]" : "") + "myCurrentTree=null, inputId=" + myInputId);
        }
        return false; // ?????????
      }
      changesProcessor.removed(myInputId, myInputId);
      if (!dryRun) updateStubIndices(null);
    }
    return true;
  }

  @Nullable
  @ApiStatus.Internal
  public SerializedStubTree getSerializedStubTree() {
    return myCurrentTree;
  }

  @Override
  public @NotNull Collection<Integer> getKeys() {
    return myCurrentTree != null ? Collections.singleton(myInputId) : Collections.emptySet();
  }

  private static boolean treesAreEqual(@NotNull SerializedStubTree newSerializedStubTree,
                                       @NotNull SerializedStubTree currentTree) {
    return Arrays.equals(currentTree.getTreeHash(), newSerializedStubTree.getTreeHash()) &&
           treesAreReallyEqual(newSerializedStubTree, currentTree);
  }

  private static boolean treesAreReallyEqual(@NotNull SerializedStubTree newSerializedStubTree,
                                             @NotNull SerializedStubTree currentTree) {
    if (newSerializedStubTree.equals(currentTree)) {
      return true;
    }
    if (IndexDebugProperties.DEBUG) {
      reportStubTreeHashCollision(newSerializedStubTree, currentTree);
    }
    return false;
  }

  private void updateStubIndices(@Nullable SerializedStubTree newTree) {
    try {
      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> oldForwardIndex =
        myCurrentTree == null ? Collections.emptyMap() : myCurrentTree.getStubIndicesValueMap();

      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> newForwardIndex =
        newTree == null ? Collections.emptyMap() : newTree.getStubIndicesValueMap();

      Collection<StubIndexKey<?, ?>> affectedIndexes =
        ContainerUtil.union(oldForwardIndex.keySet(), newForwardIndex.keySet());

      StubIndexEx stubIndex = (StubIndexEx)StubIndex.getInstance();
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        stubIndex.getLogger()
          .info("stub indexes " + (newTree == null ? "deletion" : "update") + ": file = " + myInputId + " indexes " + affectedIndexes);
      }

      //noinspection rawtypes
      for (StubIndexKey key : affectedIndexes) {
        // StubIdList-s are ignored.
        Set<Object> oldKeys = oldForwardIndex.getOrDefault(key, Collections.emptyMap()).keySet();
        Set<Object> newKeys = newForwardIndex.getOrDefault(key, Collections.emptyMap()).keySet();

        //noinspection unchecked
        stubIndex.updateIndex(key, myInputId, oldKeys, newKeys);
      }
    } catch (ProcessCanceledException e) {
      LOG.error("ProcessCanceledException is not expected here", e);
      throw e;
    }
  }

  private static void reportStubTreeHashCollision(@NotNull SerializedStubTree newTree,
                                                  @NotNull SerializedStubTree existingTree) {
    String oldTreeDump = "\nexisting tree " + dumpStub(existingTree);
    String newTreeDump = "\nnew tree " + dumpStub(newTree);
    byte[] hash = newTree.getTreeHash();
    LOG.info("Stub tree hashing collision. " +
             "Different trees have the same hash = " + StringUtil.toHexString(hash) + ". " +
             oldTreeDump + newTreeDump, new Exception());
  }

  private static @NotNull String dumpStub(@NotNull SerializedStubTree tree) {
    String deserialized;
    try {
      deserialized = "stub: " + DebugUtil.stubTreeToString(tree.getStub());
    }
    catch (SerializerNotFoundException e) {
      LOG.error(e);
      deserialized = "error while stub deserialization: " + e.getMessage();
    }
    return deserialized + "\n bytes: " + StringUtil.toHexString(tree.myTreeBytes);
  }
}
