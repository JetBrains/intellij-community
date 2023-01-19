// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CompressionUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.indexing.impl.perFileVersion.PersistentSubIndexerRetriever;
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex;
import com.intellij.util.indexing.storage.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class SnapshotInputMappings<Key, Value> implements UpdatableSnapshotInputMappingIndex<Key, Value, FileContent> {
  private static final Logger LOG = Logger.getInstance(SnapshotInputMappings.class);

  public static int getVersion() {
    assert FileBasedIndex.ourSnapshotMappingsEnabled;
    return 0xFFF +  // base index version modifier
           1 // snapshot input mappings version
      ;
  }

  private final ID<Key, Value> myIndexId;
  private final DataExternalizer<Map<Key, Value>> myMapExternalizer;
  private final DataExternalizer<Value> myValueExternalizer;
  private final DataIndexer<Key, Value, FileContent> myIndexer;
  @NotNull
  private final PersistentMapBasedForwardIndex myContents;
  @NotNull
  private final SnapshotHashEnumeratorService.HashEnumeratorHandle myEnumeratorHandle;
  private volatile PersistentHashMap<Integer, String> myIndexingTrace;
  private final @Nullable SnapshotInputMappingsStatistics myStatistics;

  private final HashIdForwardIndexAccessor<Key, Value, FileContent> myHashIdForwardIndexAccessor;

  private final CompositeHashIdEnumerator myCompositeHashIdEnumerator;

  private PersistentSubIndexerRetriever<?, ?> mySubIndexerRetriever;

  public SnapshotInputMappings(@NotNull IndexExtension<Key, Value, FileContent> indexExtension,
                               @NotNull AbstractMapForwardIndexAccessor<Key, Value, ?> accessor) throws IOException {
    myIndexId = (ID<Key, Value>)indexExtension.getName();
    myStatistics = IndexDebugProperties.DEBUG ? new SnapshotInputMappingsStatistics(myIndexId) : null;

    boolean storeOnlySingleValue = indexExtension instanceof SingleEntryFileBasedIndexExtension;
    myMapExternalizer = storeOnlySingleValue ? null : new InputMapExternalizer<>(indexExtension);
    myValueExternalizer = storeOnlySingleValue ? new NullableDataExternalizer<>(indexExtension.getValueExternalizer()) : null;

    myIndexer = indexExtension.getIndexer();
    myContents = createContentsIndex();
    myHashIdForwardIndexAccessor = new HashIdForwardIndexAccessor<>(this, accessor);
    myIndexingTrace = IndexDebugProperties.EXTRA_SANITY_CHECKS ? createIndexingTrace() : null;
    myEnumeratorHandle = SnapshotHashEnumeratorService.getInstance().createHashEnumeratorHandle(myIndexId);

    if (VfsAwareMapReduceIndex.isCompositeIndexer(myIndexer)) {
      myCompositeHashIdEnumerator = new CompositeHashIdEnumerator(myIndexId);
    } else {
      myCompositeHashIdEnumerator = null;
    }
  }

  public HashIdForwardIndexAccessor<Key, Value, FileContent> getForwardIndexAccessor() {
    return myHashIdForwardIndexAccessor;
  }

  public Path getInputIndexStorageFile() throws IOException {
    return IndexInfrastructure.getIndexRootDir(myIndexId).resolve("fileIdToHashId");
  }

  @NotNull
  @Override
  public Map<Key, Value> readData(int hashId) throws IOException {
    return ObjectUtils.notNull(doReadData(hashId), Collections.emptyMap());
  }

  @Nullable
  @Override
  public InputData<Key, Value> readData(@NotNull FileContent content) throws IOException {
    if (((FileContentImpl)content).isTransientContent()) {
      throw new IllegalArgumentException("Non-physical data are not allowed.");
    }
    int hashId = getHashId(content);

    Map<Key, Value> data = doReadData(hashId);

    if (myStatistics != null) {
      myStatistics.update(data == null);
    }

    if (data != null && IndexDebugProperties.EXTRA_SANITY_CHECKS) {
      Map<Key, Value> contentData = myIndexer.map(content);
      boolean sameValueForSavedIndexedResultAndCurrentOne;
      if (myIndexer instanceof SingleEntryIndexer) {
        Value contentValue = ContainerUtil.getFirstItem(contentData.values());
        Value value = ContainerUtil.getFirstItem(data.values());
        sameValueForSavedIndexedResultAndCurrentOne = Comparing.equal(contentValue, value);
      }
      else {
        sameValueForSavedIndexedResultAndCurrentOne = contentData.equals(data);
      }
      if (!sameValueForSavedIndexedResultAndCurrentOne) {
        LOG.error(
          "Unexpected difference in indexing of" +
          "\n" + getContentDebugData(content) +
          "\n by index " + myIndexId +
          "\nprevious indexed info " + myIndexingTrace.get(hashId) +
          "\ndiff " + buildDiff(data, contentData));
      }
    }
    return data == null ? null : new HashedInputData<>(data, hashId);
  }

  @Nullable
  private Map<Key, Value> doReadData(int hashId) throws IOException {
    ByteArraySequence byteSequence = readContents(hashId);
    return byteSequence != null ? deserialize(byteSequence) : null;
  }

  @NotNull
  private Map<Key, Value> deserialize(@NotNull ByteArraySequence byteSequence) throws IOException {
    if (myMapExternalizer != null) {
      return AbstractForwardIndexAccessor.deserializeFromByteSeq(byteSequence, myMapExternalizer);
    } else {
      assert myValueExternalizer != null;
      Value value = AbstractForwardIndexAccessor.deserializeFromByteSeq(byteSequence, myValueExternalizer);
      if (value == null && !((SingleEntryIndexer<?>)myIndexer).isAcceptNullValues()) {
        return Collections.emptyMap();
      }
      //noinspection unchecked
      return Collections.singletonMap((Key)Integer.valueOf(0), value);
    }
  }

  private @NotNull ByteArraySequence serializeData(@NotNull Map<Key, Value> data) throws IOException {
    if (myMapExternalizer != null) {
      return AbstractForwardIndexAccessor.serializeValueToByteSeq(data, myMapExternalizer, data.size() * 4);
    }
    else {
      assert myValueExternalizer != null;
      return AbstractForwardIndexAccessor.serializeValueToByteSeq(ContainerUtil.getFirstItem(data.values()), myValueExternalizer, 4);
    }
  }

  @Override
  public InputData<Key, Value> putData(@NotNull FileContent content, @NotNull InputData<Key, Value> data) throws IOException {
    int hashId;
    InputData<Key, Value> result;
    if (data instanceof HashedInputData) {
      hashId = ((HashedInputData<Key, Value>)data).getHashId();
      result = data;
    } else {
      hashId = getHashId(content);
      result = hashId == 0 ? InputData.empty() : new HashedInputData<>(data.getKeyValues(), hashId);
    }
    boolean saved = savePersistentData(data.getKeyValues(), hashId);
    if (IndexDebugProperties.EXTRA_SANITY_CHECKS) {
      if (saved) {
        try {
          myIndexingTrace.put(hashId, getContentDebugData(content) + "," + ExceptionUtil.getThrowableText(new Throwable()));
        }
        catch (IOException ex) {
          LOG.error(ex);
        }
      }
    }
    return result;
  }

  @NotNull
  private String getContentDebugData(@NotNull FileContent input) {
    FileContentImpl content = (FileContentImpl) input;

    List<String> data = new ArrayList<>();
    data.add(content.getFile().getPath());
    data.add(content.getFileType().getName());
    data.add(content.getCharset().name());
    if (VfsAwareMapReduceIndex.isCompositeIndexer(myIndexer)) {
      data.add("composite indexer: " + mySubIndexerRetriever.getVersion(content));

    }

    return "[" + StringUtil.join(data, ";") + "]";
  }

  private int getHashId(@Nullable FileContent content) throws IOException {
    if (content == null) {
      return 0;
    }
    int hash = doGetContentHash((FileContentImpl)content);
    if (myCompositeHashIdEnumerator != null) {
      int subIndexerTypeId = mySubIndexerRetriever.getFileIndexerId(content);
      hash = myCompositeHashIdEnumerator.enumerate(hash, subIndexerTypeId);
    }

    return hash;
  }

  @Override
  public void flush() {
    myContents.force();
    if (myIndexingTrace != null) myIndexingTrace.force();
    if (myCompositeHashIdEnumerator != null) myCompositeHashIdEnumerator.force();
  }

  @Override
  public void clear() throws IOException {
    try {
      if (myCompositeHashIdEnumerator != null) {
        try {
          myCompositeHashIdEnumerator.clear();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      if (myIndexingTrace != null) {
        myIndexingTrace.closeAndClean();
        myIndexingTrace = createIndexingTrace();
      }
    } finally {
      try {
        myContents.clear();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void close() {
    IOUtil.closeSafe(LOG, myContents, myIndexingTrace, myCompositeHashIdEnumerator);
    myEnumeratorHandle.release();
  }

  @NotNull
  private PersistentMapBasedForwardIndex createContentsIndex() throws IOException {
    Path saved = IndexInfrastructure.getPersistentIndexRootDir(myIndexId).resolve("values");
    try {
      return new PersistentMapBasedForwardIndex(saved, false);
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(saved);
      throw ex;
    }
  }

  private PersistentHashMap<Integer, String> createIndexingTrace() throws IOException {
    Path mapFile = IndexInfrastructure.getIndexRootDir(myIndexId).resolve("indextrace");
    try {
      return new PersistentHashMap<>(mapFile, EnumeratorIntegerDescriptor.INSTANCE, new DataExternalizer<>() {
        @Override
        public void save(@NotNull DataOutput out, String value) throws IOException {
          out.write((byte[])CompressionUtil.compressStringRawBytes(value));
        }

        @Override
        public String read(@NotNull DataInput in) throws IOException {
          byte[] b = new byte[((InputStream)in).available()];
          in.readFully(b);
          return (String)CompressionUtil.uncompressStringRawBytes(b);
        }
      }, 4096);
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(mapFile);
      throw ex;
    }
  }

  private ByteArraySequence readContents(int hashId) throws IOException {
    return myContents.get(hashId);
  }

  // TODO replace it with constructor parameter
  public void setSubIndexerRetriever(@NotNull PersistentSubIndexerRetriever<?, ?> retriever) {
    assert myCompositeHashIdEnumerator != null;
    mySubIndexerRetriever = retriever;
  }

  @NotNull
  private Integer doGetContentHash(FileContentImpl content) throws IOException {
    Integer previouslyCalculatedContentHashId = content.getUserData(ourContentHashIdKey);
    if (previouslyCalculatedContentHashId == null) {
      byte[] hash = IndexedHashesSupport.getOrInitIndexedHash(content);
      previouslyCalculatedContentHashId = myEnumeratorHandle.enumerateHash(hash);
      content.putUserData(ourContentHashIdKey, previouslyCalculatedContentHashId);
    }
    return previouslyCalculatedContentHashId;
  }
  private static final com.intellij.openapi.util.Key<Integer> ourContentHashIdKey = com.intellij.openapi.util.Key.create("saved.content.hash.id");


  private StringBuilder buildDiff(Map<Key, Value> data, Map<Key, Value> contentData) {
    StringBuilder moreInfo = new StringBuilder();
    if (contentData.size() != data.size()) {
      moreInfo.append("Indexer has different number of elements, previously ").append(data.size()).append(" after ")
        .append(contentData.size()).append("\n");
    } else {
      moreInfo.append("total ").append(contentData.size()).append(" entries\n");
    }

    for(Map.Entry<Key, Value> keyValueEntry:contentData.entrySet()) {
      if (!data.containsKey(keyValueEntry.getKey())) {
        moreInfo.append("Previous data doesn't contain:").append(keyValueEntry.getKey()).append( " with value ").append(keyValueEntry.getValue()).append("\n");
      }
      else {
        Value value = data.get(keyValueEntry.getKey());
        if (!Comparing.equal(keyValueEntry.getValue(), value)) {
          moreInfo.append("Previous data has different value for key:").append(keyValueEntry.getKey()).append( ", new value ").append(keyValueEntry.getValue()).append( ", oldValue:").append(value).append("\n");
        }
      }
    }

    for(Map.Entry<Key, Value> keyValueEntry:data.entrySet()) {
      if (!contentData.containsKey(keyValueEntry.getKey())) {
        moreInfo.append("New data doesn't contain:").append(keyValueEntry.getKey()).append( " with value ").append(keyValueEntry.getValue()).append("\n");
      }
      else {
        Value value = contentData.get(keyValueEntry.getKey());
        if (!Comparing.equal(keyValueEntry.getValue(), value)) {
          moreInfo.append("New data has different value for key:").append(keyValueEntry.getKey()).append( " new value ").append(value).append( ", oldValue:").append(keyValueEntry.getValue()).append("\n");
        }
      }
    }
    return moreInfo;
  }

  private boolean savePersistentData(@NotNull Map<Key, Value> data, int id) {
    try {
      if (myContents.containsMapping(id)) return false;
      ByteArraySequence bytes = serializeData(data);
      myContents.put(id, bytes);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return true;
  }

  @ApiStatus.Internal
  public void resetStatistics() {
    if (myStatistics != null) {
      myStatistics.reset();
    }
  }

  @ApiStatus.Internal
  public @Nullable SnapshotInputMappingsStatistics dumpStatistics() {
    return myStatistics;
  }
}
