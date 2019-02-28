// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.CompressionUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.DebugAssertions;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SnapshotInputMappings<Key, Value> implements UpdatableSnapshotIndex<Key, Value> {
  private static final Logger LOG = Logger.getInstance(SnapshotInputMappings.class);
  private static final boolean doReadSavedPersistentData = SystemProperties.getBooleanProperty("idea.read.saved.persistent.index", true);

  private final ID<Key, Value> myIndexId;
  private final MapDataExternalizer<Key, Value> myMapExternalizer;
  private final DataIndexer<Key, Value, FileContent> myIndexer;
  private volatile PersistentHashMap<Integer, ByteArraySequence> myContents;
  private volatile PersistentHashMap<Integer, String> myIndexingTrace;

  private final FileContentHasher myHasher;

  public SnapshotInputMappings(IndexExtension<Key, Value, FileContent> indexExtension) throws IOException {
    myIndexId = (ID<Key, Value>)indexExtension.getName();
    myMapExternalizer = new MapDataExternalizer<>(indexExtension);
    myIndexer = indexExtension.getIndexer();
    myHasher = new Sha1FileContentHasher(indexExtension instanceof PsiDependentIndex);
    createMaps();
  }

  @Nullable
  @Override
  public Map<Key, Value> readSnapshot(int hashId) throws IOException {
    ByteArraySequence byteSequence = readContents(hashId);
    return byteSequence == null ? null : deserializeSavedPersistentData(byteSequence);
  }

  @Override
  public Map<Key, Value> readOrPutSnapshot(int hashId, @NotNull FileContent content) throws IOException {
    Map<Key, Value> data = null;
    boolean havePersistentData = false;
    boolean skippedReadingPersistentDataButMayHaveIt = false;

    hashId = myHasher.getEnumeratedHash(content);
    if (doReadSavedPersistentData) {
      if (myContents == null || !myContents.isBusyReading() || DebugAssertions.EXTRA_SANITY_CHECKS) { // avoid blocking read, we can calculate index value
        ByteArraySequence bytes = readContents(hashId);

        if (bytes != null) {
          data = deserializeSavedPersistentData(bytes);
          havePersistentData = true;
          if (DebugAssertions.EXTRA_SANITY_CHECKS) {
            Map<Key, Value> contentData = myIndexer.map(content);
            boolean sameValueForSavedIndexedResultAndCurrentOne = contentData.equals(data);
            if (!sameValueForSavedIndexedResultAndCurrentOne) {
              DebugAssertions.error(
                "Unexpected difference in indexing of %s by index %s, file type %s, charset %s\ndiff %s\nprevious indexed info %s",
                content.getFile(),
                myIndexId,
                content.getFileType().getName(),
                ((FileContentImpl)content).getCharset(),
                buildDiff(data, contentData),
                myIndexingTrace.get(hashId)
              );
            }
          }
        }
      }
      else {
        skippedReadingPersistentDataButMayHaveIt = true;
      }
    }
    else {
      havePersistentData = myContents.containsMapping(hashId);
    }

    if (data == null) {
      data = myIndexer.map(content);
      if (DebugAssertions.DEBUG) {
        MapReduceIndex.checkValuesHaveProperEqualsAndHashCode(data, myIndexId, myMapExternalizer.getValueExternalizer());
      }
    }

    if (!havePersistentData) {
      boolean saved = savePersistentData(data, hashId, skippedReadingPersistentDataButMayHaveIt);
      if (DebugAssertions.EXTRA_SANITY_CHECKS) {
        if (saved) {

          try {
            myIndexingTrace.put(hashId, ((FileContentImpl)content).getCharset() +
                                        "," +
                                        content.getFileType().getName() +
                                        "," +
                                        content.getFile().getPath() +
                                        "," +
                                        ExceptionUtil.getThrowableText(new Throwable()));
          }
          catch (IOException ex) {
            LOG.error(ex);
          }
        }
      }
    }
    return data;
  }

  @Override
  public void flush() {
    if (myContents != null) myContents.force();
    if (myIndexingTrace != null) myIndexingTrace.force();
  }

  @Override
  public void clear() throws IOException {
    List<File> baseDirs = ContainerUtil.list(myContents, myIndexingTrace)
      .stream()
      .filter(Objects::nonNull)
      .map(PersistentHashMap::getBaseFile)
      .collect(Collectors.toList());
    try {
      close();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    baseDirs.forEach(PersistentHashMap::deleteFilesStartingWith);
    createMaps();
  }

  @Override
  public void close() throws IOException {
    if (myContents != null) myContents.close();
    if (myIndexingTrace != null) myIndexingTrace.close();
  }

  private void createMaps() throws IOException {
    myContents = createContentsIndex();
    myIndexingTrace = DebugAssertions.EXTRA_SANITY_CHECKS ? createIndexingTrace() : null;
  }

  private PersistentHashMap<Integer, ByteArraySequence> createContentsIndex() throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled && !SharedIndicesData.DO_CHECKS) return null;
    final File saved = new File(IndexInfrastructure.getPersistentIndexRootDir(myIndexId), "values");
    try {
      return new PersistentHashMap<>(saved, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(saved);
      throw ex;
    }
  }

  private PersistentHashMap<Integer, String> createIndexingTrace() throws IOException {
    final File mapFile = new File(IndexInfrastructure.getIndexRootDir(myIndexId), "indextrace");
    try {
      return new PersistentHashMap<>(mapFile, EnumeratorIntegerDescriptor.INSTANCE,
                                     new DataExternalizer<String>() {
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

  private ByteArraySequence readContents(Integer hashId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (SharedIndicesData.DO_CHECKS) {
        synchronized (myContents) {
          ByteArraySequence contentBytes = SharedIndicesData.recallContentData(hashId, myIndexId, ByteSequenceDataExternalizer.INSTANCE);
          ByteArraySequence contentBytesFromContents = myContents.get(hashId);

          if (contentBytes == null && contentBytesFromContents != null ||
              !Comparing.equal(contentBytesFromContents, contentBytes)) {
            SharedIndicesData.associateContentData(hashId, myIndexId, contentBytesFromContents, ByteSequenceDataExternalizer.INSTANCE);
            if (contentBytes != null) {
              LOG.error("Unexpected indexing diff with hash id " + myIndexId + "," + hashId);
            }
            contentBytes = contentBytesFromContents;
          }
          return contentBytes;
        }
      } else {
        return SharedIndicesData.recallContentData(hashId, myIndexId, ByteSequenceDataExternalizer.INSTANCE);
      }
    }

    return myContents.get(hashId);
  }

  private Map<Key, Value> deserializeSavedPersistentData(ByteArraySequence bytes) throws IOException {
    DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getLength()));

    return myMapExternalizer.read(stream);
  }

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

  private static final ThreadLocalCachedByteArray ourSpareByteArray = new ThreadLocalCachedByteArray();
  private boolean savePersistentData(Map<Key, Value> data, int id, boolean delayedReading) {
    try {
      if (delayedReading && myContents.containsMapping(id)) return false;
      BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(ourSpareByteArray.getBuffer(4 * data.size()));
      DataOutputStream stream = new DataOutputStream(out);
      myMapExternalizer.save(stream, data);

      saveContents(id, out);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return true;
  }

  private void saveContents(int id, BufferExposingByteArrayOutputStream out) throws IOException {
    ByteArraySequence byteSequence = out.toByteArraySequence();
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (SharedIndicesData.DO_CHECKS) {
        synchronized (myContents) {
          myContents.put(id, byteSequence);
          SharedIndicesData.associateContentData(id, myIndexId, byteSequence, ByteSequenceDataExternalizer.INSTANCE);
        }
      } else {
        SharedIndicesData.associateContentData(id, myIndexId, byteSequence, ByteSequenceDataExternalizer.INSTANCE);
      }
    } else {
      myContents.put(id, byteSequence);
    }
  }
}
