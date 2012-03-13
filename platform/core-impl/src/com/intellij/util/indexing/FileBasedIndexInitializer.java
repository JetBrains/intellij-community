/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class FileBasedIndexInitializer
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndexInitializer");
  @NonNls
  private static final String CORRUPTION_MARKER_NAME = "corruption.marker";
  private ScheduledFuture<?> myFlushingFuture;
  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  private FileBasedIndex myFileBasedIndex;
  private FileBasedIndexIndicesManager myIndexIndicesManager;
  private AbstractVfsAdapter myVfsAdapter;
  private VirtualFileManagerEx myVfManager;
  private IndexingStamp myIndexingStamp;
  private FileBasedIndexLimitsChecker myLimitsChecker;
  private FileDocumentManager myFileDocumentManager;


  protected FileBasedIndexInitializer(FileBasedIndex fileBasedIndex, FileBasedIndexIndicesManager indexIndicesManager, AbstractVfsAdapter vfsAdapter,
                                      final VirtualFileManagerEx vfManager,
                                      IndexingStamp indexingStamp,
                                      FileBasedIndexLimitsChecker limitsChecker,
                                      FileDocumentManager fileDocumentManager) {
    myFileBasedIndex = fileBasedIndex;
    myIndexIndicesManager = indexIndicesManager;
    myVfsAdapter = vfsAdapter;
    myVfManager = vfManager;
    myIndexingStamp = indexingStamp;
    myLimitsChecker = limitsChecker;
    myFileDocumentManager = fileDocumentManager;
  }

  public void disposeComponent() {
    performShutdown();
  }

  public void initComponent()  {
    try {
      performInit();
    }
    catch (IOException e) {
      LOG.error("Error while initialazing FileBasedIndexInitializer", e);
    }
  }

  private void performInit() throws IOException {
    /*
    final File workInProgressFile = getMarkerFile();
    if (workInProgressFile.exists()) {
      // previous IDEA session was closed incorrectly, so drop all indices
      FileUtil.delete(PathManager.getIndexRoot());
    }
    */

    try {
      final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        myFileBasedIndex.getRebuildStatus().put(extension.getName(), new AtomicInteger(FileBasedIndex.REBUILD_OK));
      }

      final File corruptionMarker = new File(PathManager.getIndexRoot(), CORRUPTION_MARKER_NAME);
      final boolean currentVersionCorrupted = corruptionMarker.exists();
      boolean versionChanged = false;
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        versionChanged |= registerIndexer(extension, currentVersionCorrupted);
      }
      FileUtil.delete(corruptionMarker);

      String rebuildNotification = null;
      if (currentVersionCorrupted) {
        rebuildNotification = "Index files on disk are corrupted. Indices will be rebuilt.";
      }
      else if (versionChanged) {
        rebuildNotification = "Index file format has changed for some indices. These indices will be rebuilt.";
      }
      if (rebuildNotification != null
          && Registry.is("ide.showIndexRebuildMessage")) {
        myFileBasedIndex.notifyIndexRebuild(rebuildNotification);
      }

      dropUnregisteredIndices();

      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myIndexIndicesManager.keySet()) {
        if (myFileBasedIndex.getRebuildStatus().get(indexId).compareAndSet(FileBasedIndex.REQUIRES_REBUILD, FileBasedIndex.REBUILD_OK)) {
          try {
            myFileBasedIndex.clearIndex(indexId);
          }
          catch (StorageException e) {
            myFileBasedIndex.requestRebuild(indexId);
            LOG.error(e);
          }
        }
      }

      myVfManager.addVirtualFileListener(myFileBasedIndex.getChangedFilesCollector());

      IndexableFileSet additionalIndexableFileSet = myVfsAdapter.getAdditionalIndexableFileSet();
      if(additionalIndexableFileSet != null)
        myFileBasedIndex.registerIndexableSet(additionalIndexableFileSet, null);
    }
    finally {
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        @Override
        public void run() {
          performShutdown();
        }
      });
      //FileUtil.createIfDoesntExist(workInProgressFile);
      saveRegisteredIndices(myIndexIndicesManager.keySet());
      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        int lastModCount = 0;

        @Override
        public void run() {
          if (lastModCount == myFileBasedIndex.getLocalModCount()) {
            flushAllIndices(lastModCount);
          }
          lastModCount = myFileBasedIndex.getLocalModCount();
        }
      });

    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = readRegisteredIndexNames();
    for (ID<?, ?> key : myIndexIndicesManager.keySet()) {
      indicesToDrop.remove(key.toString());
    }
    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  private static Set<String> readRegisteredIndexNames() {
    final Set<String> result = new HashSet<String>();
    try {
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(getRegisteredIndicesFile())));
      try {
        final int size = in.readInt();
        for (int idx = 0; idx < size; idx++) {
          result.add(IOUtil.readString(in));
        }
      }
      finally {
        in.close();
      }
    }
    catch (IOException ignored) {
    }
    return result;
  }

  private static File getRegisteredIndicesFile() {
    return new File(PathManager.getIndexRoot(), "registered");
  }



  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   *
   * @param extension
   * @param isCurrentVersionCorrupted
   */
  private <K, V> boolean registerIndexer(final FileBasedIndexExtension<K, V> extension, final boolean isCurrentVersionCorrupted) throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    final boolean versionFileExisted = versionFile.exists();
    boolean versionChanged = false;
    if (isCurrentVersionCorrupted || IndexInfrastructure.versionDiffers(versionFile, version)) {
      if (!isCurrentVersionCorrupted && versionFileExisted) {
        versionChanged = true;
        LOG.info("Version has changed for index " + extension.getName() + ". The index will be rebuilt.");
      }
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getCacheSize());
        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(name, extension, memStorage);
        final FileBasedIndex.InputFilter inputFilter = extension.getInputFilter();

        assert inputFilter != null : "Index extension " + name + " must provide non-null input filter";

        myIndexIndicesManager.addNewIndex(name,
                                          new Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter>(index, new IndexableFilesFilter(inputFilter)));

        myFileBasedIndex.putIndex(name, version);
        if (!extension.dependsOnFileContent()) {
          myFileBasedIndex.addNotRequiringContentIndex(name);
        }
        else {
          myFileBasedIndex.addRequiringContentIndices(name);
        }
        myLimitsChecker.addNoLimitsFileTypes(extension.getFileTypesWithSizeLimitNotApplicable());
        break;
      }
      catch (IOException e) {
        LOG.info(e);
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
    return versionChanged;
  }

  private <K, V> UpdatableIndex<K, V, FileContent> createIndex(final ID<K, V> indexId, final FileBasedIndexExtension<K, V> extension, final MemoryIndexStorage<K, V> storage) throws IOException {
    final MapReduceIndex<K, V, FileContent> index;
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      final UpdatableIndex<K, V, FileContent> custom = ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension)
        .createIndexImplementation(indexId, myFileBasedIndex, storage);

      assert custom != null : "Custom index implementation must not be null; index: " + indexId;

      if (!(custom instanceof MapReduceIndex)) {
        return custom;
      }
      index = (MapReduceIndex<K,V, FileContent>)custom;
    }
    else {
      index = new MapReduceIndex<K, V, FileContent>(indexId, extension.getIndexer(), storage);
    }

    final KeyDescriptor<K> keyDescriptor = extension.getKeyDescriptor();
    index.setInputIdToDataKeysIndex(new Factory<PersistentHashMap<Integer, Collection<K>>>() {
      @Override
      public PersistentHashMap<Integer, Collection<K>> create() {
        try {
          return createIdToDataKeysIndex(indexId, keyDescriptor, storage);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    return index;
  }

  private static <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(final ID<K, ?> indexId,
                                                                                       final KeyDescriptor<K> keyDescriptor,
                                                                                       MemoryIndexStorage<K, ?> storage) throws IOException {
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);
    final Ref<Boolean> isBufferingMode = new Ref<Boolean>(false);
    final Map<Integer, Collection<K>> tempMap = new HashMap<Integer, Collection<K>>();

    final DataExternalizer<Collection<K>> dataExternalizer = new DataExternalizer<Collection<K>>() {
      @Override
      public void save(DataOutput out, Collection<K> value) throws IOException {
        try {
          DataInputOutputUtil.writeINT(out, value.size());
          for (K key : value) {
            keyDescriptor.save(out, key);
          }
        }
        catch (IllegalArgumentException e) {
          throw new IOException("Error saving data for index " + indexId, e);
        }
      }

      @Override
      public Collection<K> read(DataInput in) throws IOException {
        try {
          final int size = DataInputOutputUtil.readINT(in);
          final List<K> list = new ArrayList<K>(size);
          for (int idx = 0; idx < size; idx++) {
            list.add(keyDescriptor.read(in));
          }
          return list;
        }
        catch (IllegalArgumentException e) {
          throw new IOException("Error reading data for index " + indexId, e);
        }
      }
    };

    // Important! Update IdToDataKeysIndex depending on the sate of "buffering" flag from the MemoryStorage.
    // If buffering is on, all changes should be done in memory (similar to the way it is done in memory storage).
    // Otherwise data in IdToDataKeysIndex will not be in sync with the 'main' data in the index on disk and index updates will be based on the
    // wrong sets of keys for the given file. This will lead to unpredictable results in main index because it will not be
    // cleared properly before updating (removed data will still be present on disk). See IDEA-52223 for illustration of possible effects.

    final PersistentHashMap<Integer, Collection<K>> map = new PersistentHashMap<Integer, Collection<K>>(
      indexStorageFile, new EnumeratorIntegerDescriptor(), dataExternalizer
    ) {

      @Override
      protected Collection<K> doGet(Integer integer) throws IOException {
        if (isBufferingMode.get()) {
          final Collection<K> collection = tempMap.get(integer);
          if (collection != null) {
            return collection;
          }
        }
        return super.doGet(integer);
      }

      @Override
      protected void doPut(Integer integer, Collection<K> ks) throws IOException {
        if (isBufferingMode.get()) {
          tempMap.put(integer, ks == null? Collections.<K>emptySet() : ks);
        }
        else {
          super.doPut(integer, ks);
        }
      }

      @Override
      protected void doRemove(Integer integer) throws IOException {
        if (isBufferingMode.get()) {
          tempMap.put(integer, Collections.<K>emptySet());
        }
        else {
          super.doRemove(integer);
        }
      }
    };

    storage.addBufferingStateListsner(new MemoryIndexStorage.BufferingStateListener() {
      @Override
      public void bufferingStateChanged(boolean newState) {
        synchronized (map) {
          isBufferingMode.set(newState);
        }
      }
      @Override
      public void memoryStorageCleared() {
        synchronized (map) {
          tempMap.clear();
        }
      }
    });
    return map;
  }


  private static void saveRegisteredIndices(Collection<ID<?, ?>> ids) {
    final File file = getRegisteredIndicesFile();
    try {
      FileUtil.createIfDoesntExist(file);
      final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        os.writeInt(ids.size());
        for (ID<?, ?> id : ids) {
          IOUtil.writeString(id.toString(), os);
        }
      }
      finally {
        os.close();
      }
    }
    catch (IOException ignored) {
    }
  }


  private void performShutdown() {
    if (!myShutdownPerformed.compareAndSet(false, true)) {
      return; // already shut down
    }
    try {
      if (myFlushingFuture != null) {
        myFlushingFuture.cancel(false);
        myFlushingFuture = null;
      }

      myFileDocumentManager.saveAllDocuments();
    }
    finally {
      LOG.info("START INDEX SHUTDOWN");
      try {
        myFileBasedIndex.getChangedFilesCollector().forceUpdate(null, null, null, true);

        for (ID<?, ?> indexId : myIndexIndicesManager.keySet()) {
          final UpdatableIndex<?, ?, FileContent> index = myIndexIndicesManager.getIndex(indexId);
          assert index != null;
          myFileBasedIndex.checkRebuild(indexId, true); // if the index was scheduled for rebuild, only clean it
          //LOG.info("DISPOSING " + indexId);
          index.dispose();
        }

        myVfManager.removeVirtualFileListener(myFileBasedIndex.getChangedFilesCollector());

        //FileUtil.delete(getMarkerFile());
      }
      catch (Throwable e) {
        LOG.info("Problems during index shutdown", e);
        throw new RuntimeException(e);
      }
      LOG.info("END INDEX SHUTDOWN");
    }
  }

  private void flushAllIndices(final long modCount) {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      return;
    }
    myIndexingStamp.flushCache();
    for (ID<?, ?> indexId : new ArrayList<ID<?, ?>>(myIndexIndicesManager.keySet())) {
      if (HeavyProcessLatch.INSTANCE.isRunning() || modCount != myFileBasedIndex.getLocalModCount()) {
        return; // do not interfere with 'main' jobs
      }
      try {
        final UpdatableIndex<?, ?, FileContent> index = myIndexIndicesManager.getIndex(indexId);
        if (index != null) {
          index.flush();
        }
      }
      catch (StorageException e) {
        LOG.info(e);
        myFileBasedIndex.requestRebuild(indexId);
      }
    }

    if (!HeavyProcessLatch.INSTANCE.isRunning() && modCount == myFileBasedIndex.getLocalModCount()) { // do not interfere with 'main' jobs
      SerializationManager.getInstance().flushNameStorage();
    }
  }

  private static class IndexableFilesFilter implements FileBasedIndex.InputFilter {
    private final FileBasedIndex.InputFilter myDelegate;

    private IndexableFilesFilter(FileBasedIndex.InputFilter delegate) {
      myDelegate = delegate;
    }

    @Override
    public boolean acceptInput(final VirtualFile file) {
      return file instanceof VirtualFileWithId && myDelegate.acceptInput(file);
    }
  }
}
