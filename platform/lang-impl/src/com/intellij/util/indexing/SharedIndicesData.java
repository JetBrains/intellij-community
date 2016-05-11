/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;

public class SharedIndicesData {
  private static final TIntIntHashMap ourRegisteredIndices = new TIntIntHashMap();
  private static PersistentHashMap<Integer, byte[]> ourSharedFileInputs;
  private static PersistentHashMap<Integer, byte[]> ourSharedFileContentIndependentInputs;
  private static PersistentHashMap<Integer, byte[]> ourSharedContentInputs;
  static final boolean ourFileSharedIndicesEnabled = SystemProperties.getBooleanProperty("idea.shared.input.index.enabled", true);
  static final boolean DO_CHECKS = ourFileSharedIndicesEnabled && SystemProperties.getBooleanProperty("idea.shared.input.index.checked", true);

  //private static ScheduledFuture<?> ourFlushingFuture;
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  @SuppressWarnings({"FieldCanBeLocal", "unused"}) private static LowMemoryWatcher myLowMemoryCallback;

  static {
    if (ourFileSharedIndicesEnabled) {
      try {
        myLowMemoryCallback = LowMemoryWatcher.register(new Runnable() {
          @Override
          public void run() {
            ourFileIndexedStates.clear();
            ourFileIndexedStates2.clear();
            ourContentIndexedStates.clear();
          }
        });
        ourSharedFileInputs = createSharedMap(new File(PathManager.getIndexRoot(), "file_inputs.data"));
        ourSharedFileContentIndependentInputs = createSharedMap(new File(PathManager.getIndexRoot(), "file_inputs_content_independent.data"));
        ourSharedContentInputs = createSharedMap(new File(IndexInfrastructure.getPersistentIndexRoot(), "content_inputs.data"));

        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          @Override
          public void run() {
            close(ourSharedFileInputs);
            close(ourSharedFileContentIndependentInputs);
            close(ourSharedContentInputs);
          }

          private void close(PersistentHashMap<Integer, byte[]> index) {
            try {
              index.close();
            } catch (IOException ex) {
              LOG.error(ex);
            }
          }
        });
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private static final IndexedStateCache ourFileIndexedStates = new IndexedStateCache(200, 100, ourSharedFileInputs);
  private static final IndexedStateCache ourFileIndexedStates2 = new IndexedStateCache(200, 100, ourSharedFileContentIndependentInputs);
  private static final IndexedStateCache ourContentIndexedStates = new IndexedStateCache(200, 100, ourSharedContentInputs);

  private static PersistentHashMap<Integer, byte[]> createSharedMap(final File indexFile) throws IOException {
    return IOUtil.openCleanOrResetBroken(
      new ThrowableComputable<PersistentHashMap<Integer, byte[]>, IOException>() {
        @Override
        public PersistentHashMap<Integer, byte[]> compute() throws IOException {
          return new PersistentHashMap<Integer, byte[]>(indexFile, EnumeratorIntegerDescriptor.INSTANCE,
                                                        new DataExternalizer<byte[]>() {
                                                          @Override
                                                          public void save(@NotNull DataOutput out, byte[] value) throws IOException {
                                                            out.write(value);
                                                          }

                                                          @Override
                                                          public byte[] read(@NotNull DataInput in) throws IOException {
                                                            int available = ((InputStream)in).available();
                                                            byte[] result = new byte[available];
                                                            in.readFully(result);
                                                            return result;
                                                          }
                                                        });
        }
      }, indexFile);
  }

  public static void init() {
  }

  private static final int CONTENTLESS = 1;
  private static final int CONTENTFUL = 2;

  public static <Key, Value, Input> void registerIndex(ID<Key, Value> indexId, IndexExtension<Key, Value, Input> extension) {
    if (extension instanceof FileBasedIndexExtension) {
      boolean dependsOnFileContent = ((FileBasedIndexExtension)extension).dependsOnFileContent();
      ourRegisteredIndices.put(indexId.getUniqueId(), dependsOnFileContent ? CONTENTFUL : CONTENTLESS);
    }
  }

  public static void flushData() {
    if (!ourFileSharedIndicesEnabled) return;
    doForce(ourFileIndexedStates);
    doForce(ourFileIndexedStates2);
    doForce(ourContentIndexedStates);
  }

  protected static void doForce(IndexedStateCache indexedStates) {
    indexedStates.clear();
    if (indexedStates.myStorage != null && indexedStates.myStorage.isDirty()) indexedStates.myStorage.force();
  }

  public static void beforeSomeIndexVersionInvalidation() {
    flushData();
  }

  static class IndexedState {
    private final int fileOrContentId;
    private final PersistentHashMap<Integer, byte[]> storage;

    private byte[] values;
    private TIntLongHashMap indexId2Offset;
    private TIntObjectHashMap<byte[]> indexId2NewState;
    private boolean compactNecessary;

    IndexedState(int fileOrContentId, PersistentHashMap<Integer, byte[]> storage) throws IOException {
      this.fileOrContentId = fileOrContentId;
      this.storage = storage;
      byte[] bytes = storage.get(fileOrContentId);
      if (bytes == null) {
        return;
      }

      DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes));
      boolean compactNecessary = false;
      TIntLongHashMap stateMap = null;

      while(stream.available() > 0) {
        int chunkSize = DataInputOutputUtil.readINT(stream);
        int chunkIndexId = DataInputOutputUtil.readINT(stream);
        long chunkIndexTimeStamp = DataInputOutputUtil.readTIME(stream);
        int currentOffset = bytes.length - stream.available();

        ID<?, ?> chunkIndexID;
        if (((chunkIndexID = ID.findById(chunkIndexId)) != null &&
             chunkIndexTimeStamp == IndexingStamp.getIndexCreationStamp(chunkIndexID))
          ) {
          if (chunkSize != 0) {
            if (stateMap == null) stateMap = new TIntLongHashMap();
            stateMap.put(chunkIndexId, (((long)currentOffset) << 32) | chunkSize);
          } else if (stateMap != null) {
            stateMap.remove(chunkIndexId);
            compactNecessary = true;
          }
        } else {
          compactNecessary = true;
        }

        stream.skipBytes(chunkSize);
      }
      values = bytes;
      this.compactNecessary = compactNecessary;
      indexId2Offset = stateMap;
    }

    synchronized void flush() throws IOException {
      if (compactNecessary) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        UnsyncByteArrayOutputStream compactedOutputStream = new UnsyncByteArrayOutputStream(values.length);
        //noinspection IOResourceOpenedButNotSafelyClosed
        DataOutput compactedOutput = new DataOutputStream(compactedOutputStream);

        Ref<IOException> ioExceptionRef = new Ref<>();

        boolean result = indexId2NewState == null || indexId2NewState.forEachEntry(new TIntObjectProcedure<byte[]>() {
          @Override
          public boolean execute(int indexUniqueId, byte[] indexValue) {
            try {
              long indexCreationStamp = IndexingStamp.getIndexCreationStamp(ID.findById(indexUniqueId));

              writeIndexValue(indexUniqueId, indexCreationStamp, indexValue, 0, indexValue.length, compactedOutput);

              return true;
            }
            catch (IOException ex) {
              ioExceptionRef.set(ex);
              return false;
            }
          }
        });
        if (!result) throw ioExceptionRef.get();

        result = indexId2Offset == null || indexId2Offset.forEachEntry(new TIntLongProcedure() {
          @Override
          public boolean execute(int chunkIndexId, long chunkOffsetAndSize) {
            try {
              int chunkOffset = (int)(chunkOffsetAndSize >> 32);
              int chunkSize = (int)chunkOffsetAndSize;

              writeIndexValue(
                chunkIndexId,
                IndexingStamp.getIndexCreationStamp(ID.findById(chunkIndexId)),
                values,
                chunkOffset,
                chunkSize,
                compactedOutput
              );

              return true;
            }
            catch (IOException e) {
              ioExceptionRef.set(e);
              return false;
            }
          }
        });
        if (!result) throw ioExceptionRef.get();
        if (compactedOutputStream.size() > 0) storage.put(fileOrContentId, compactedOutputStream.toByteArray());
        else storage.remove(fileOrContentId);
      }
    }

    // todo: what about handling changed indices' versions
    synchronized void appendIndexedState(ID<?, ?> indexId, long timestamp, byte[] buffer, int size) {
      int indexUniqueId = indexId.getUniqueId();

      if (indexId2Offset != null) indexId2Offset.remove(indexUniqueId);
      if (buffer == null) {
        if (indexId2NewState != null) indexId2NewState.remove(indexUniqueId);
      } else {
        if (indexId2NewState == null) indexId2NewState = new TIntObjectHashMap<>();
        indexId2NewState.put(indexUniqueId, Arrays.copyOf(buffer, size));
      }
    }

    synchronized @Nullable DataInputStream readIndexedState(ID<?, ?> indexId) {
      int indexUniqueId = indexId.getUniqueId();
      int offset = 0;
      int length = 0;
      byte[] bytes = null;

      if (indexId2NewState != null) { // newdata
        bytes = indexId2NewState.get(indexUniqueId);
        offset = 0;
        length = bytes != null ? bytes.length : 0;
      }

      if (bytes == null) {
        if (values == null || // empty
            indexId2Offset == null ||
            !indexId2Offset.contains(indexUniqueId) // no previous data
          ) {
          return null;
        }
        bytes = values;
        long offsetAndSize = indexId2Offset.get(indexUniqueId);
        offset = (int)(offsetAndSize >> 32);
        length = (int)offsetAndSize;
      }

      return new DataInputStream(new UnsyncByteArrayInputStream(bytes, offset, offset + length));
    }
  }

  private static void writeIndexValue(int indexUniqueId,
                                      long indexCreationStamp,
                                      byte[] indexValue,
                                      int indexValueOffset, int indexValueLength,
                                      DataOutput compactedOutput) throws IOException {
    DataInputOutputUtil.writeINT(compactedOutput, indexValueLength);
    DataInputOutputUtil.writeINT(compactedOutput, indexUniqueId);

    DataInputOutputUtil.writeTIME(compactedOutput, indexCreationStamp);
    if (indexValue != null) {
      assert indexValueLength > 0;
      compactedOutput.write(indexValue, indexValueOffset, indexValueLength);
    }
    else {
      assert indexValueLength == 0;
    }
  }

  // Record:  (<chunkSize> <indexId> <indexStamp> <SavedData>)*

  public static @Nullable <Key, Value> Value recallFileData(int id, ID<Key, ?> indexId, DataExternalizer<Value> externalizer)
    throws IOException {
    int type = ourRegisteredIndices.get(indexId.getUniqueId());
    if (type == 0) return null;

    return doRecallData(id, indexId, externalizer, type == CONTENTLESS ? ourFileIndexedStates2 : ourFileIndexedStates);
  }

  public static @Nullable <Key, Value> Value recallContentData(int id, ID<Key, ?> indexId, DataExternalizer<Value> externalizer)
    throws IOException {
    return doRecallData(id, indexId, externalizer, ourContentIndexedStates);
  }

  @Nullable
  private static <Key, Value> Value doRecallData(int id,
                                                 ID<Key, ?> indexId,
                                                 DataExternalizer<Value> externalizer,
                                                 FileAccessorCache<Integer, IndexedState> states)
    throws IOException {
    FileAccessorCache.Handle<IndexedState> stateHandle = states.get(id);
    IndexedState indexedState = stateHandle.get();

    try {
      DataInputStream in = indexedState.readIndexedState(indexId);
      if (in == null) return null;
      return externalizer.read(in);
    } finally {
      stateHandle.release();
    }
  }

  public static <Key, Value> void associateFileData(int id, ID<Key, ?> indexId, Value keys, DataExternalizer<Value> externalizer)
    throws IOException {
    int type = ourRegisteredIndices.get(indexId.getUniqueId());
    if (type == 0) return;
    boolean contentlessIndex = type == CONTENTLESS;
    doAssociateData(id, indexId, keys, externalizer,
                    contentlessIndex ? ourFileIndexedStates2 : ourFileIndexedStates,
                    contentlessIndex ? ourSharedFileContentIndependentInputs : ourSharedFileInputs);
  }

  public static <Key, Value> void associateContentData(int id, ID<Key, ?> indexId, Value keys, DataExternalizer<Value> externalizer)
    throws IOException {
    doAssociateData(id, indexId, keys, externalizer, ourContentIndexedStates, ourSharedContentInputs);
  }

  private static <Key, Value> void doAssociateData(int id,
                                                   final ID<Key, ?> indexId,
                                                   Value keys,
                                                   DataExternalizer<Value> externalizer,
                                                   FileAccessorCache<Integer, IndexedState> states,
                                                   PersistentHashMap<Integer, byte[]> index)
    throws IOException {
    final BufferExposingByteArrayOutputStream savedKeysData;
    if (keys != null) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      externalizer.save(new DataOutputStream(savedKeysData = new BufferExposingByteArrayOutputStream()), keys);
    } else {
      savedKeysData = null;
    }

    FileAccessorCache.Handle<IndexedState> stateHandle = states.getIfCached(id);

    try {
      index.appendData(id, new PersistentHashMap.ValueDataAppender() {
        @Override
        public void append(DataOutput out) throws IOException {
          byte[] internalBuffer = null;
          int size = 0;
          if (savedKeysData != null) {
            internalBuffer = savedKeysData.getInternalBuffer();
            size = savedKeysData.size();
          }

          long indexCreationStamp = IndexingStamp.getIndexCreationStamp(indexId);
          writeIndexValue(
            indexId.getUniqueId(),
            indexCreationStamp,
            internalBuffer,
            0,
            size,
            out
          );

          final IndexedState indexedState = stateHandle != null ? stateHandle.get() : null;
          if (indexedState != null) {
            indexedState.appendIndexedState(indexId, indexCreationStamp, internalBuffer, size);
          }
        }
      });
    } finally {
      if (stateHandle != null) stateHandle.release();
    }
  }

  private static class IndexedStateCache extends FileAccessorCache<Integer, IndexedState> {
    private final PersistentHashMap<Integer, byte[]> myStorage;
    public IndexedStateCache(int protectedQueueSize,
                             int probationalQueueSize,
                             PersistentHashMap<Integer, byte[]> storage) {
      super(protectedQueueSize, probationalQueueSize);
      myStorage = storage;
    }

    @Override
    protected IndexedState createAccessor(Integer key) throws IOException {
      return new IndexedState(key, myStorage);
    }

    @Override
    protected void disposeAccessor(IndexedState fileAccessor) throws IOException {
      fileAccessor.flush();
    }
  }
}
