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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.CompressionUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.DebugAssertions;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class SnapshotInputMappings<Key, Value, Input> {
  private static final Logger LOG = Logger.getInstance(SnapshotInputMappings.class);
  private static final boolean doReadSavedPersistentData = SystemProperties.getBooleanProperty("idea.read.saved.persistent.index", true);

  private final ID<Key, Value> myIndexId;
  private final VfsAwareMapReduceIndex.MapDataExternalizer<Key, Value> myMapExternalizer;
  private final DataIndexer<Key, Value, Input> myIndexer;
  private volatile PersistentHashMap<Integer, ByteArraySequence> myContents;
  private volatile PersistentHashMap<Integer, Integer> myInputsSnapshotMapping;
  private volatile PersistentHashMap<Integer, String> myIndexingTrace;

  private boolean myIsPsiBackedIndex;

  public SnapshotInputMappings(IndexExtension<Key, Value, Input> indexExtension) throws IOException {
    myIndexId = (ID<Key, Value>)indexExtension.getName();
    myIsPsiBackedIndex = indexExtension instanceof PsiDependentIndex;
    myMapExternalizer = new VfsAwareMapReduceIndex.MapDataExternalizer<>(indexExtension);
    myIndexer = indexExtension.getIndexer();
    createMaps();
  }

  @NotNull
  public Map<Key, Value> readInputKeys(int inputId) throws IOException {
    Integer currentHashId = readInputHashId(inputId);
    if (currentHashId != null) {
      ByteArraySequence byteSequence = readContents(currentHashId);
      if (byteSequence != null) {
        return deserializeSavedPersistentData(byteSequence);
      }
    }
    return Collections.emptyMap();
  }

  static class Snapshot<Key, Value> {
    private final Map<Key, Value> myData;
    private final int hashId;

    private Snapshot(Map<Key, Value> data, int id) {
      myData = data;
      hashId = id;
    }

    public Map<Key, Value> getData() {
      return myData;
    }

    public int getHashId() {
      return hashId;
    }
  }

  @NotNull
  Snapshot<Key, Value> readPersistentDataOrMap(@NotNull Input content) {
    Map<Key, Value> data = null;
    boolean havePersistentData = false;
    int hashId;
    boolean skippedReadingPersistentDataButMayHaveIt = false;

    try {
      FileContent fileContent = (FileContent)content;
      hashId = getHashOfContent(fileContent);
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
                  fileContent.getFile(),
                  myIndexId,
                  fileContent.getFileType().getName(),
                  ((FileContentImpl)fileContent).getCharset(),
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
    }
    catch (IOException ex) {
      // todo:
      throw new RuntimeException(ex);
    }

    if (data == null) {
      data = myIndexer.map(content);
      if (DebugAssertions.DEBUG) {
        MapReduceIndex.checkValuesHaveProperEqualsAndHashCode(data, myIndexId, myMapExternalizer.myValueExternalizer);
      }
    }

    if (!havePersistentData) {
      boolean saved = savePersistentData(data, hashId, skippedReadingPersistentDataButMayHaveIt);
      if (DebugAssertions.EXTRA_SANITY_CHECKS) {
        if (saved) {

          FileContent fileContent = (FileContent)content;
          try {
            myIndexingTrace.put(hashId, ((FileContentImpl)fileContent).getCharset() +
                                        "," +
                                        fileContent.getFileType().getName() +
                                        "," +
                                        fileContent.getFile().getPath() +
                                        "," +
                                        ExceptionUtil.getThrowableText(new Throwable()));
          }
          catch (IOException ex) {
            LOG.error(ex);
          }
        }
      }
    }

    return new Snapshot<>(data, hashId);
  }

  public void putInputHash(int inputId, int hashId)
    throws IOException {
    try {
      if (SharedIndicesData.ourFileSharedIndicesEnabled) {
        SharedIndicesData.associateFileData(inputId, myIndexId, hashId, EnumeratorIntegerDescriptor.INSTANCE);
      }
      if (myInputsSnapshotMapping != null) myInputsSnapshotMapping.put(inputId, hashId);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void flush() {
    if (myContents != null) myContents.force();
    if (myInputsSnapshotMapping != null) myInputsSnapshotMapping.force();
    if (myIndexingTrace != null) myIndexingTrace.force();
  }

  public void clear() throws IOException {
    List<File> baseDirs = ContainerUtil.list(myContents, myIndexingTrace, myInputsSnapshotMapping)
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

  public void close() throws IOException {
    if (myContents != null) myContents.close();
    if (myInputsSnapshotMapping != null) myInputsSnapshotMapping.close();
    if (myIndexingTrace != null) myIndexingTrace.close();
  }

  private void createMaps() throws IOException {
    myContents = createContentsIndex();
    myIndexingTrace = DebugAssertions.EXTRA_SANITY_CHECKS ? createIndexingTrace() : null;
    myInputsSnapshotMapping =
      !SharedIndicesData.ourFileSharedIndicesEnabled || SharedIndicesData.DO_CHECKS ? createInputSnapshotMapping() : null;
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

  private PersistentHashMap<Integer, Integer> createInputSnapshotMapping() throws IOException {
    final File fileIdToHashIdFile = new File(IndexInfrastructure.getIndexRootDir(myIndexId), "fileIdToHashId");
    try {
      return new PersistentHashMap<Integer, Integer>(fileIdToHashIdFile, EnumeratorIntegerDescriptor.INSTANCE,
                                                     EnumeratorIntegerDescriptor.INSTANCE, 4096) {
        @Override
        protected boolean wantNonnegativeIntegralValues() {
          return true;
        }
      };
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(fileIdToHashIdFile);
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
                                         out.write((byte[])CompressionUtil.compressCharSequence(value, Charset.defaultCharset()));
                                       }

                                       @Override
                                       public String read(@NotNull DataInput in) throws IOException {
                                         byte[] b = new byte[((InputStream)in).available()];
                                         in.readFully(b);
                                         return (String)CompressionUtil.uncompressCharSequence(b, Charset.defaultCharset());
                                       }
                                     }, 4096);
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(mapFile);
      throw ex;
    }
  }

  private Integer readInputHashId(int inputId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      Integer hashId = SharedIndicesData.recallFileData(inputId, myIndexId, EnumeratorIntegerDescriptor.INSTANCE);
      if (hashId == null) hashId = 0;
      if (myInputsSnapshotMapping == null) return hashId;

      Integer hashIdFromInputSnapshotMapping = myInputsSnapshotMapping.get(inputId);
      if ((hashId == 0 && hashIdFromInputSnapshotMapping != 0) ||
          !Comparing.equal(hashIdFromInputSnapshotMapping, hashId)) {
        SharedIndicesData.associateFileData(inputId, myIndexId, hashIdFromInputSnapshotMapping,
                                            EnumeratorIntegerDescriptor.INSTANCE);
        if (hashId != 0) {
          LOG.error("Unexpected indexing diff with hashid " +
                    myIndexId +
                    ", file:" +
                    IndexInfrastructure.findFileById(PersistentFS.getInstance(), inputId)
                    +
                    "," +
                    hashIdFromInputSnapshotMapping +
                    "," +
                    hashId);
        }
        hashId = hashIdFromInputSnapshotMapping;
      }
      return hashId;
    }
    return myInputsSnapshotMapping.get(inputId);
  }

  private ByteArraySequence readContents(Integer hashId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (SharedIndicesData.DO_CHECKS) {
        synchronized (myContents) {
          ByteArraySequence contentBytes = SharedIndicesData.recallContentData(hashId, myIndexId, ByteSequenceDataExternalizer.INSTANCE);
          ByteArraySequence contentBytesFromContents = myContents.get(hashId);

          if ((contentBytes == null && contentBytesFromContents != null) ||
              !Comparing.equal(contentBytesFromContents, contentBytes)) {
            SharedIndicesData.associateContentData(hashId, myIndexId, contentBytesFromContents, ByteSequenceDataExternalizer.INSTANCE);
            if (contentBytes != null) {
              LOG.error("Unexpected indexing diff with hashid " + myIndexId + "," + hashId);
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

  private Integer getHashOfContent(FileContent content) throws IOException {
    FileType fileType = content.getFileType();
    if (myIsPsiBackedIndex && content instanceof FileContentImpl) {
      // psi backed index should use existing psi to build index value (FileContentImpl.getPsiFileForPsiDependentIndex())
      // so we should use different bytes to calculate hash(Id)
      Integer previouslyCalculatedUncommittedHashId = content.getUserData(ourSavedUncommittedHashIdKey);

      if (previouslyCalculatedUncommittedHashId == null) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(content.getFile());

        if (document != null) {  // if document is not committed
          PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(content.getProject());

          if (psiDocumentManager.isUncommited(document)) {
            PsiFile file = psiDocumentManager.getCachedPsiFile(document);
            Charset charset = ((FileContentImpl)content).getCharset();

            if (file != null) {
              previouslyCalculatedUncommittedHashId = ContentHashesSupport
                .calcContentHashIdWithFileType(file.getText().getBytes(charset), charset,
                                               fileType);
              content.putUserData(ourSavedUncommittedHashIdKey, previouslyCalculatedUncommittedHashId);
            }
          }
        }
      }
      if (previouslyCalculatedUncommittedHashId != null) return previouslyCalculatedUncommittedHashId;
    }

    Integer previouslyCalculatedContentHashId = content.getUserData(ourSavedContentHashIdKey);
    if (previouslyCalculatedContentHashId == null) {
      byte[] hash = content instanceof FileContentImpl ? ((FileContentImpl)content).getHash():null;
      if (hash == null) {
        if (fileType.isBinary()) {
          previouslyCalculatedContentHashId = ContentHashesSupport.calcContentHashId(content.getContent(), fileType);
        } else {
          Charset charset = content instanceof FileContentImpl ? ((FileContentImpl)content).getCharset() : null;
          previouslyCalculatedContentHashId = ContentHashesSupport
            .calcContentHashIdWithFileType(content.getContent(), charset, fileType);
        }
      } else {
        previouslyCalculatedContentHashId =  ContentHashesSupport.enumerateHash(hash);
      }
      content.putUserData(ourSavedContentHashIdKey, previouslyCalculatedContentHashId);
    }
    return previouslyCalculatedContentHashId;
  }
  private static final com.intellij.openapi.util.Key<Integer> ourSavedContentHashIdKey = com.intellij.openapi.util.Key.create("saved.content.hash.id");
  private static final com.intellij.openapi.util.Key<Integer> ourSavedUncommittedHashIdKey = com.intellij.openapi.util.Key.create("saved.uncommitted.hash.id");


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
    ByteArraySequence byteSequence = new ByteArraySequence(out.getInternalBuffer(), 0, out.size());
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
