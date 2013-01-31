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

import com.intellij.AppTopics;
import com.intellij.history.LocalHistory;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.lang.ASTNode;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.EditorHighlighterCache;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.psi.stubs.SerializationManagerEx;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.*;
import jsr166e.SequenceLock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 * @since Dec 20, 2007
 */
public class FileBasedIndexImpl extends FileBasedIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndexImpl");
  @NonNls
  private static final String CORRUPTION_MARKER_NAME = "corruption.marker";
  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices =
    new THashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final Map<ID<?, ?>, Semaphore> myUnsavedDataIndexingSemaphores = new THashMap<ID<?, ?>, Semaphore>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNotRequiringContentIndices = new THashSet<ID<?, ?>>();
  private final Set<ID<?, ?>> myRequiringContentIndices = new THashSet<ID<?, ?>>();
  private final Set<FileType> myNoLimitCheckTypes = new THashSet<FileType>();

  private final PerIndexDocumentVersionMap myLastIndexedDocStamps = new PerIndexDocumentVersionMap();
  @NotNull private final ChangedFilesCollector myChangedFilesCollector;

  private final List<IndexableFileSet> myIndexableSets = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<IndexableFileSet, Project> myIndexableSetToProjectMap = new THashMap<IndexableFileSet, Project>();

  private static final int OK = 1;
  private static final int REQUIRES_REBUILD = 2;
  private static final int REBUILD_IN_PROGRESS = 3;
  private static final Map<ID<?, ?>, AtomicInteger> ourRebuildStatus = new THashMap<ID<?, ?>, AtomicInteger>();

  private final VirtualFileManager myVfManager;
  private final FileDocumentManager myFileDocumentManager;
  private final FileTypeManager myFileTypeManager;
  private final ConcurrentHashSet<ID<?, ?>> myUpToDateIndices = new ConcurrentHashSet<ID<?, ?>>();
  private final Map<Document, PsiFile> myTransactionMap = new THashMap<Document, PsiFile>();

  private static final int ALREADY_PROCESSED = 0x04;

  @Nullable private final String myConfigPath;
  @Nullable private final String myLogPath;
  private final boolean myIsUnitTestMode;
  @Nullable private ScheduledFuture<?> myFlushingFuture;
  private volatile int myLocalModCount;
  private volatile int myFilesModCount;
  private final AtomicInteger myUpdatingFiles = new AtomicInteger();
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"}) private volatile boolean myInitialized;
  // need this variable for memory barrier

  public FileBasedIndexImpl(VirtualFileManager vfManager,
                            FileDocumentManager fdm,
                            FileTypeManager fileTypeManager,
                            @NotNull MessageBus bus,
                            @SuppressWarnings("UnusedParameters") SerializationManager sm /*needed to ensure dependency*/) {
    myVfManager = vfManager;
    myFileDocumentManager = fdm;
    myFileTypeManager = fileTypeManager;
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    myConfigPath = calcConfigPath(PathManager.getConfigPath());
    myLogPath = calcConfigPath(PathManager.getLogPath());

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(final Document doc, final PsiFile file) {
        if (file != null) {
          synchronized (myTransactionMap) {
            myTransactionMap.put(doc, file);
          }
          myUpToDateIndices.clear();
        }
      }

      @Override
      public void transactionCompleted(final Document doc, final PsiFile file) {
        synchronized (myTransactionMap) {
          myTransactionMap.remove(doc);
        }
      }
    });

    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Nullable private Map<FileType, Set<String>> myTypeToExtensionMap;

      @Override
      public void beforeFileTypesChanged(final FileTypeEvent event) {
        cleanupProcessedFlag();
        myTypeToExtensionMap = new THashMap<FileType, Set<String>>();
        for (FileType type : myFileTypeManager.getRegisteredFileTypes()) {
          myTypeToExtensionMap.put(type, getExtensions(type));
        }
      }

      @Override
      public void fileTypesChanged(final FileTypeEvent event) {
        final Map<FileType, Set<String>> oldExtensions = myTypeToExtensionMap;
        myTypeToExtensionMap = null;
        if (oldExtensions != null) {
          final Map<FileType, Set<String>> newExtensions = new THashMap<FileType, Set<String>>();
          for (FileType type : myFileTypeManager.getRegisteredFileTypes()) {
            newExtensions.put(type, getExtensions(type));
          }
          // we are interested only in extension changes or removals.
          // addition of an extension is handled separately by RootsChanged event
          if (!newExtensions.keySet().containsAll(oldExtensions.keySet())) {
            rebuildAllIndices();
            return;
          }
          for (Map.Entry<FileType, Set<String>> entry : oldExtensions.entrySet()) {
            FileType fileType = entry.getKey();
            Set<String> strings = entry.getValue();
            if (!newExtensions.get(fileType).containsAll(strings)) {
              rebuildAllIndices();
              return;
            }
          }
        }
      }

      @NotNull
      private Set<String> getExtensions(@NotNull FileType type) {
        final Set<String> set = new THashSet<String>();
        for (FileNameMatcher matcher : myFileTypeManager.getAssociations(type)) {
          set.add(matcher.getPresentableString());
        }
        return set;
      }

      private void rebuildAllIndices() {
        for (ID<?, ?> indexId : myIndices.keySet()) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            LOG.info(e);
          }
        }
        scheduleIndexRebuild(true);
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          final Object requestor = event.getRequestor();
          if (requestor instanceof FileDocumentManager ||
              requestor instanceof PsiManager ||
              requestor == LocalHistory.VFS_EVENT_REQUESTOR) {
            cleanupMemoryStorage();
            break;
          }
        }
      }
    });

    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentReloaded(VirtualFile file, @NotNull Document document) {
        cleanupMemoryStorage();
      }

      @Override
      public void unsavedDocumentsDropped() {
        cleanupMemoryStorage();
      }
    });

    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
      @Override
      public void writeActionStarted(Object action) {
        myUpToDateIndices.clear();
      }
    });

    myChangedFilesCollector = new ChangedFilesCollector();
  }

  @Override
  public void requestReindex(@NotNull final VirtualFile file) {
    myChangedFilesCollector.invalidateIndices(file, true);
  }

  @Override
  public void requestReindexExcluded(@NotNull final VirtualFile file) {
    myChangedFilesCollector.invalidateIndices(file, false);
  }

  private void initExtensions() {
    try {
      final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        ourRebuildStatus.put(extension.getName(), new AtomicInteger(OK));
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
          && !ApplicationManager.getApplication().isHeadlessEnvironment()
          && Registry.is("ide.showIndexRebuildMessage")) {
        new NotificationGroup("Indexing", NotificationDisplayType.BALLOON, false)
          .createNotification("Index Rebuild", rebuildNotification, NotificationType.INFORMATION, null).notify(null);
      }

      dropUnregisteredIndices();

      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myIndices.keySet()) {
        if (ourRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, OK)) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.error(e);
          }
        }
      }

      myVfManager.addVirtualFileListener(myChangedFilesCollector);

      registerIndexableSet(new AdditionalIndexableFileSet(), null);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        @Override
        public void run() {
          performShutdown();
        }
      });
      saveRegisteredIndices(myIndices.keySet());
      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        private int lastModCount = 0;

        @Override
        public void run() {
          if (lastModCount == myLocalModCount) {
            flushAllIndices(lastModCount);
          }
          lastModCount = myLocalModCount;
        }
      });
      myInitialized = true; // this will ensure that all changes to component's state will be visible to other threads
    }
  }

  @Override
  public void initComponent() {
    initExtensions();
  }

  @Nullable
  private static String calcConfigPath(@NotNull String path) {
    try {
      final String _path = FileUtil.toSystemIndependentName(new File(path).getCanonicalPath());
      return _path.endsWith("/") ? _path : _path + "/";
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  /**
   * @param extension
   * @param isCurrentVersionCorrupted
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   */
  private <K, V> boolean registerIndexer(@NotNull final FileBasedIndexExtension<K, V> extension, final boolean isCurrentVersionCorrupted)
    throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    final boolean versionFileExisted = versionFile.exists();
    boolean versionChanged = false;
    if (isCurrentVersionCorrupted || IndexInfrastructure.versionDiffers(versionFile, version)) {
      if (!isCurrentVersionCorrupted && versionFileExisted) {
        versionChanged = true;
        LOG.info("Version has changed for index " + name + ". The index will be rebuilt.");
      }
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    MapIndexStorage<K, V> storage = null;

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        storage = new MapIndexStorage<K, V>(
          IndexInfrastructure.getStorageFile(name),
          extension.getKeyDescriptor(),
          extension.getValueExternalizer(),
          extension.getCacheSize(),
          extension.isKeyHighlySelective()
        );
        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(name, extension, memStorage);
        final InputFilter inputFilter = extension.getInputFilter();

        assert inputFilter != null : "Index extension " + name + " must provide non-null input filter";

        myIndices.put(name, new Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>(index, new IndexableFilesFilter(inputFilter)));
        myUnsavedDataIndexingSemaphores.put(name, new Semaphore());
        myIndexIdToVersionMap.put(name, version);
        if (!extension.dependsOnFileContent()) {
          myNotRequiringContentIndices.add(name);
        }
        else {
          myRequiringContentIndices.add(name);
        }
        myNoLimitCheckTypes.addAll(extension.getFileTypesWithSizeLimitNotApplicable());
        break;
      }
      catch (Exception e) {
        LOG.info(e);
        try {
          if (storage != null) storage.close();
          storage = null;
        }
        catch (Exception ignored) {
        }

        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
    return versionChanged;
  }

  private static void saveRegisteredIndices(@NotNull Collection<ID<?, ?>> ids) {
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

  @NotNull
  private static Set<String> readRegisteredIndexNames() {
    final Set<String> result = new THashSet<String>();
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

  @NotNull
  private static File getRegisteredIndicesFile() {
    return new File(PathManager.getIndexRoot(), "registered");
  }

  @NotNull
  private <K, V> UpdatableIndex<K, V, FileContent> createIndex(@NotNull final ID<K, V> indexId,
                                                               @NotNull final FileBasedIndexExtension<K, V> extension,
                                                               @NotNull final MemoryIndexStorage<K, V> storage)
    throws StorageException, IOException {
    final MapReduceIndex<K, V, FileContent> index;
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      final UpdatableIndex<K, V, FileContent> custom =
        ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension).createIndexImplementation(indexId, this, storage);
      if (!(custom instanceof MapReduceIndex)) {
        return custom;
      }
      index = (MapReduceIndex<K, V, FileContent>)custom;
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

  @NotNull
  private static <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(@NotNull final ID<K, ?> indexId,
                                                                                       @NotNull final KeyDescriptor<K> keyDescriptor,
                                                                                       @NotNull MemoryIndexStorage<K, ?> storage)
    throws IOException {
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);
    final Ref<Boolean> isBufferingMode = new Ref<Boolean>(false);
    final TIntObjectHashMap<Collection<K>> tempMap = new TIntObjectHashMap<Collection<K>>();

    final DataExternalizer<Collection<K>> dataExternalizer = new DataExternalizer<Collection<K>>() {
      @Override
      public void save(DataOutput out, @NotNull Collection<K> value) throws IOException {
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

      @NotNull
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
      indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, dataExternalizer
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
      protected void doPut(Integer integer, @Nullable Collection<K> ks) throws IOException {
        if (isBufferingMode.get()) {
          tempMap.put(integer, ks == null ? Collections.<K>emptySet() : ks);
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

  @Override
  public void disposeComponent() {
    performShutdown();
  }

  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  private void performShutdown() {
    if (!myShutdownPerformed.compareAndSet(false, true)) {
      return; // already shut down
    }
    try {
      if (myFlushingFuture != null) {
        myFlushingFuture.cancel(false);
        myFlushingFuture = null;
      }

      //myFileDocumentManager.saveAllDocuments(); // rev=Eugene Juravlev
    }
    finally {
      LOG.info("START INDEX SHUTDOWN");
      try {
        myChangedFilesCollector.forceUpdate(null, null, null, true);
        IndexingStamp.flushCache(null);

        for (ID<?, ?> indexId : myIndices.keySet()) {
          final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
          assert index != null;
          checkRebuild(indexId, true); // if the index was scheduled for rebuild, only clean it
          index.dispose();
        }

        myVfManager.removeVirtualFileListener(myChangedFilesCollector);

        //FileUtil.delete(getMarkerFile());
      }
      catch (Throwable e) {
        LOG.error("Problems during index shutdown", e);
      }
      LOG.info("END INDEX SHUTDOWN");
    }
  }

  private void flushAllIndices(final long modCount) {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      return;
    }
    IndexingStamp.flushCache(null);
    for (ID<?, ?> indexId : new ArrayList<ID<?, ?>>(myIndices.keySet())) {
      if (HeavyProcessLatch.INSTANCE.isRunning() || modCount != myLocalModCount) {
        return; // do not interfere with 'main' jobs
      }
      try {
        final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
        if (index != null) {
          index.flush();
        }
      }
      catch (StorageException e) {
        LOG.info(e);
        requestRebuild(indexId);
      }
    }

    if (!HeavyProcessLatch.INSTANCE.isRunning() && modCount == myLocalModCount) { // do not interfere with 'main' jobs
      SerializationManagerEx.getInstanceEx().flushNameStorage();
    }
  }

  /**
   * @param project it is guaranteed to return data which is up-to-date withing the project
   *                Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull final ID<K, ?> indexId, @NotNull Project project) {
    Set<K> allKeys = new THashSet<K>();
    processAllKeys(indexId, new CommonProcessors.CollectProcessor<K>(allKeys), project);
    return allKeys;
  }

  /**
   * @param project it is guaranteed to return data which is up-to-date withing the project
   *                Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  @Override
  public <K> boolean processAllKeys(@NotNull final ID<K, ?> indexId, Processor<K> processor, @Nullable Project project) {
    try {
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (index == null) {
        return true;
      }
      ensureUpToDate(indexId, project, project != null ? GlobalSearchScope.allScope(project) : new EverythingGlobalScope());
      return index.processAllKeys(processor);
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }

    return false;
  }

  private static final ThreadLocal<Integer> myUpToDateCheckState = new ThreadLocal<Integer>();

  public static void disableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    myUpToDateCheckState.set(currentValue == null ? 1 : currentValue.intValue() + 1);
  }

  public static void enableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    if (currentValue != null) {
      final int newValue = currentValue.intValue() - 1;
      if (newValue != 0) {
        myUpToDateCheckState.set(newValue);
      }
      else {
        myUpToDateCheckState.remove();
      }
    }
  }

  private static boolean isUpToDateCheckEnabled() {
    final Integer value = myUpToDateCheckState.get();
    return value == null || value.intValue() == 0;
  }


  private final ThreadLocal<Boolean> myReentrancyGuard = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  @Override
  public <K> void ensureUpToDate(@NotNull final ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter) {
    ensureUpToDate(indexId, project, filter, null);
  }

  protected <K> void ensureUpToDate(@NotNull final ID<K, ?> indexId,
                                    @Nullable Project project,
                                    @Nullable GlobalSearchScope filter,
                                    @Nullable VirtualFile restrictedFile) {
    if (!needsFileContentLoading(indexId)) {
      return; //indexed eagerly in foreground while building unindexed file list
    }
    if (filter == GlobalSearchScope.EMPTY_SCOPE) {
      return;
    }
    if (isDumb(project)) {
      handleDumbMode(project);
    }

    if (myReentrancyGuard.get().booleanValue()) {
      //assert false : "ensureUpToDate() is not reentrant!";
      return;
    }
    myReentrancyGuard.set(Boolean.TRUE);

    try {
      myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
      if (isUpToDateCheckEnabled()) {
        try {
          checkRebuild(indexId, false);
          myChangedFilesCollector.forceUpdate(project, filter, restrictedFile, false);
          indexUnsavedDocuments(indexId, project, filter, restrictedFile);
        }
        catch (StorageException e) {
          scheduleRebuild(indexId, e);
        }
        catch (RuntimeException e) {
          final Throwable cause = e.getCause();
          if (cause instanceof StorageException || cause instanceof IOException) {
            scheduleRebuild(indexId, e);
          }
          else {
            throw e;
          }
        }
      }
    }
    finally {
      myReentrancyGuard.set(Boolean.FALSE);
    }
  }

  private static void handleDumbMode(@Nullable Project project) {
    ProgressManager.checkCanceled(); // DumbModeAction.CANCEL

    if (project != null) {
      final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator instanceof BackgroundableProcessIndicator) {
        final BackgroundableProcessIndicator indicator = (BackgroundableProcessIndicator)progressIndicator;
        if (indicator.getDumbModeAction() == DumbModeAction.WAIT) {
          assert !ApplicationManager.getApplication().isDispatchThread();
          DumbService.getInstance(project).waitForSmartMode();
          return;
        }
      }
    }

    throw new IndexNotReadyException();
  }

  private static boolean isDumb(@Nullable Project project) {
    if (project != null) {
      return DumbServiceImpl.getInstance(project).isDumb();
    }
    for (Project proj : ProjectManager.getInstance().getOpenProjects()) {
      if (DumbServiceImpl.getInstance(proj).isDumb()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public <K, V> List<V> getValues(@NotNull final ID<K, V> indexId, @NotNull K dataKey, @NotNull final GlobalSearchScope filter) {
    final List<V> values = new SmartList<V>();
    processValuesImpl(indexId, dataKey, true, null, new ValueProcessor<V>() {
      @Override
      public boolean process(final VirtualFile file, final V value) {
        values.add(value);
        return true;
      }
    }, filter);
    return values;
  }

  @Override
  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(@NotNull final ID<K, V> indexId,
                                                           @NotNull K dataKey,
                                                           @NotNull final GlobalSearchScope filter) {
    final Set<VirtualFile> files = new THashSet<VirtualFile>();
    processValuesImpl(indexId, dataKey, false, null, new ValueProcessor<V>() {
      @Override
      public boolean process(final VirtualFile file, final V value) {
        files.add(file);
        return true;
      }
    }, filter);
    return files;
  }


  /**
   * @return false if ValueProcessor.process() returned false; true otherwise or if ValueProcessor was not called at all
   */
  @Override
  public <K, V> boolean processValues(@NotNull final ID<K, V> indexId, @NotNull final K dataKey, @Nullable final VirtualFile inFile,
                                      @NotNull ValueProcessor<V> processor, @NotNull final GlobalSearchScope filter) {
    return processValuesImpl(indexId, dataKey, false, inFile, processor, filter);
  }


  @Nullable
  private <K, V, R> R processExceptions(@NotNull final ID<K, V> indexId,
                                        @Nullable final VirtualFile restrictToFile,
                                        @NotNull final GlobalSearchScope filter,
                                        @NotNull ThrowableConvertor<UpdatableIndex<K, V, FileContent>, R, StorageException> computable) {
    try {
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return null;
      }
      final Project project = filter.getProject();
      //assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      ensureUpToDate(indexId, project, filter, restrictToFile);

      try {
        index.getReadLock().lock();
        return computable.convert(index);
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = getCauseToRebuildIndex(e);
      if (cause != null) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    return null;
  }

  private <K, V> boolean processValuesImpl(@NotNull final ID<K, V> indexId, final K dataKey, final boolean ensureValueProcessedOnce,
                                           @Nullable final VirtualFile restrictToFile, @NotNull final ValueProcessor<V> processor,
                                           @NotNull final GlobalSearchScope filter) {
    ThrowableConvertor<UpdatableIndex<K, V, FileContent>, Boolean, StorageException> keyProcessor =
      new ThrowableConvertor<UpdatableIndex<K, V, FileContent>, Boolean, StorageException>() {
        @Override
        public Boolean convert(@NotNull UpdatableIndex<K, V, FileContent> index) throws StorageException {
          final ValueContainer<V> container = index.getData(dataKey);

          boolean shouldContinue = true;

          if (restrictToFile != null) {
            if (restrictToFile instanceof VirtualFileWithId) {
              final int restrictedFileId = getFileId(restrictToFile);
              for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
                final V value = valueIt.next();
                if (container.isAssociated(value, restrictedFileId)) {
                  shouldContinue = processor.process(restrictToFile, value);
                  if (!shouldContinue) {
                    break;
                  }
                }
              }
            }
          }
          else {
            final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
            ProjectIndexableFilesFilter projectFilesSet = projectIndexableFiles(filter.getProject());
            VALUES_LOOP:
            for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
              final V value = valueIt.next();
              for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext(); ) {
                final int id = inputIdsIterator.next();
                if (projectFilesSet != null && !projectFilesSet.contains(id)) continue;
                VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
                if (file != null && filter.accept(file)) {
                  shouldContinue = processor.process(file, value);
                  if (!shouldContinue) {
                    break VALUES_LOOP;
                  }
                  if (ensureValueProcessedOnce) {
                    break; // continue with the next value
                  }
                }
              }
            }
          }
          return shouldContinue;
        }
      };
    final Boolean result = processExceptions(indexId, restrictToFile, filter, keyProcessor);
    return result == null || result.booleanValue();
  }

  @Override
  public <K, V> boolean processFilesContainingAllKeys(@NotNull final ID<K, V> indexId,
                                                      @NotNull final Collection<K> dataKeys,
                                                      @NotNull final GlobalSearchScope filter,
                                                      @Nullable Condition<V> valueChecker,
                                                      @NotNull final Processor<VirtualFile> processor) {
    ProjectIndexableFilesFilter filesSet = projectIndexableFiles(filter.getProject());
    final TIntHashSet set = collectFileIdsContainingAllKeys(indexId, dataKeys, filter, valueChecker, filesSet);
    return set != null && processVirtualFiles(set, filter, processor);
  }

  private static final Key<SoftReference<ProjectIndexableFilesFilter>> ourProjectFilesSetKey = Key.create("projectFiles");

  public static final class ProjectIndexableFilesFilter {
    private static final int SHIFT = 6;
    private static final int MASK = (1 << SHIFT) - 1;
    private final long[] myBitMask;
    private final int myModificationCount;
    private final int myMinId;
    private final int myMaxId;

    private ProjectIndexableFilesFilter(@NotNull TIntArrayList set, int modificationCount) {
      myModificationCount = modificationCount;
      final int[] minMax = new int[2];
      if (!set.isEmpty()) {
        minMax[0] = minMax[1] = set.get(0);
      }
      set.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          minMax[0] = Math.min(minMax[0], value);
          minMax[1] = Math.max(minMax[1], value);
          return true;
        }
      });
      myMaxId = minMax[1];
      myMinId = minMax[0];
      myBitMask = new long[((myMaxId - myMinId) >> SHIFT) + 1];
      set.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          value -= myMinId;
          myBitMask[value >> SHIFT] |= (1L << (value & MASK));
          return true;
        }
      });
    }

    public boolean contains(int id) {
      if (id < myMinId) return false;
      if (id > myMaxId) return false;
      id -= myMinId;
      return (myBitMask[id >> SHIFT] & (1L << (id & MASK))) != 0;
    }
  }

  void filesUpdateFinished() {
    if (myUpdatingFiles.decrementAndGet() == 0) {
      ++myFilesModCount;
    }
  }

  private final Lock myCalcIndexableFilesLock = new SequenceLock();

  @Nullable
  public ProjectIndexableFilesFilter projectIndexableFiles(@Nullable Project project) {
    if (project == null || myUpdatingFiles.get() > 0) return null;

    SoftReference<ProjectIndexableFilesFilter> reference = project.getUserData(ourProjectFilesSetKey);
    ProjectIndexableFilesFilter data = reference != null ? reference.get() : null;
    if (data != null && data.myModificationCount == myFilesModCount) return data;

    if (myCalcIndexableFilesLock.tryLock()) { // make best effort for calculating filter
      try {
        reference = project.getUserData(ourProjectFilesSetKey);
        data = reference != null ? reference.get() : null;
        if (data != null && data.myModificationCount == myFilesModCount) {
          return data;
        }

        long start = System.currentTimeMillis();

        final TIntArrayList filesSet = new TIntArrayList();
        iterateIndexableFiles(new ContentIterator() {
          @Override
          public boolean processFile(@NotNull VirtualFile fileOrDir) {
            filesSet.add(((VirtualFileWithId)fileOrDir).getId());
            return true;
          }
        }, project, SilentProgressIndicator.create());
        ProjectIndexableFilesFilter filter = new ProjectIndexableFilesFilter(filesSet, myFilesModCount);
        project.putUserData(ourProjectFilesSetKey, new SoftReference<ProjectIndexableFilesFilter>(filter));

        long finish = System.currentTimeMillis();
        LOG.debug(filesSet.size() + " files iterated in " + (finish - start) + " ms");

        return filter;
      }
      finally {
        myCalcIndexableFilesLock.unlock();
      }
    }
    return null; // ok, no filtering
  }

  @Nullable
  private <K, V> TIntHashSet collectFileIdsContainingAllKeys(@NotNull final ID<K, V> indexId,
                                                             @NotNull final Collection<K> dataKeys,
                                                             @NotNull final GlobalSearchScope filter,
                                                             @Nullable final Condition<V> valueChecker,
                                                             @Nullable final ProjectIndexableFilesFilter projectFilesFilter) {
    final ThrowableConvertor<UpdatableIndex<K, V, FileContent>, TIntHashSet, StorageException> convertor =
      new ThrowableConvertor<UpdatableIndex<K, V, FileContent>, TIntHashSet, StorageException>() {
        @Nullable
        @Override
        public TIntHashSet convert(@NotNull UpdatableIndex<K, V, FileContent> index) throws StorageException {
          TIntHashSet mainIntersection = null;

          for (K dataKey : dataKeys) {
            ProgressManager.checkCanceled();
            final TIntHashSet copy = new TIntHashSet();
            final ValueContainer<V> container = index.getData(dataKey);

            for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
              final V value = valueIt.next();
              if (valueChecker != null && !valueChecker.value(value)) {
                continue;
              }

              ValueContainer.IntIterator iterator = container.getInputIdsIterator(value);

              if (mainIntersection == null || iterator.size() < mainIntersection.size()) {
                while (iterator.hasNext()) {
                  final int id = iterator.next();
                  if (mainIntersection == null && (projectFilesFilter == null || projectFilesFilter.contains(id)) ||
                      mainIntersection != null && mainIntersection.contains(id)
                    ) {
                    copy.add(id);
                  }
                }
              }
              else {
                mainIntersection.forEach(new TIntProcedure() {
                  final ValueContainer.IntPredicate predicate = container.getValueAssociationPredicate(value);

                  @Override
                  public boolean execute(int id) {
                    if (predicate.contains(id)) copy.add(id);
                    return true;
                  }
                });
              }
            }

            mainIntersection = copy;
            if (mainIntersection.isEmpty()) {
              return new TIntHashSet();
            }
          }

          return mainIntersection;
        }
      };


    return processExceptions(indexId, null, filter, convertor);
  }

  private static boolean processVirtualFiles(@NotNull TIntHashSet ids,
                                             @NotNull final GlobalSearchScope filter,
                                             @NotNull final Processor<VirtualFile> processor) {
    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    return ids.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int id) {
        ProgressManager.checkCanceled();
        VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
        if (file != null && filter.accept(file)) {
          return processor.process(file);
        }
        return true;
      }
    });
  }

  @Nullable
  public static Throwable getCauseToRebuildIndex(@NotNull RuntimeException e) {
    Throwable cause = e.getCause();
    if (cause instanceof StorageException || cause instanceof IOException ||
        cause instanceof IllegalArgumentException) return cause;
    return null;
  }

  @Override
  public <K, V> boolean getFilesWithKey(@NotNull final ID<K, V> indexId,
                                        @NotNull final Set<K> dataKeys,
                                        @NotNull Processor<VirtualFile> processor,
                                        @NotNull GlobalSearchScope filter) {
    try {
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return true;
      }
      final Project project = filter.getProject();
      //assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      ensureUpToDate(indexId, project, filter);

      try {
        index.getReadLock().lock();
        final List<TIntHashSet> locals = new ArrayList<TIntHashSet>();
        for (K dataKey : dataKeys) {
          TIntHashSet local = new TIntHashSet();
          locals.add(local);
          final ValueContainer<V> container = index.getData(dataKey);

          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext(); ) {
              final int id = inputIdsIterator.next();
              local.add(id);
            }
          }
        }

        if (locals.isEmpty()) {
          return true;
        }

        Collections.sort(locals, new Comparator<TIntHashSet>() {
          @Override
          public int compare(TIntHashSet o1, TIntHashSet o2) {
            return o1.size() - o2.size();
          }
        });

        final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
        TIntIterator ids = join(locals).iterator();
        ProjectIndexableFilesFilter projectIndexableFilesFilter = projectIndexableFiles(project);
        while (ids.hasNext()) {
          int id = ids.next();
          if (projectIndexableFilesFilter != null && !projectIndexableFilesFilter.contains(id)) continue;
          //VirtualFile file = IndexInfrastructure.findFileById(fs, id);
          VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
          if (file != null && filter.accept(file)) {
            if (!processor.process(file)) {
              return false;
            }
          }
        }
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    return true;
  }

  @NotNull
  private static TIntHashSet join(@NotNull List<TIntHashSet> locals) {
    TIntHashSet result = locals.get(0);
    if (locals.size() > 1) {
      TIntIterator it = result.iterator();

      while (it.hasNext()) {
        int id = it.next();
        for (int i = 1; i < locals.size(); i++) {
          if (!locals.get(i).contains(id)) {
            it.remove();
            break;
          }
        }
      }
    }
    return result;
  }

  @Override
  public <K> void scheduleRebuild(@NotNull final ID<K, ?> indexId, @NotNull final Throwable e) {
    LOG.info(e);
    requestRebuild(indexId, new Throwable(e));
    try {
      checkRebuild(indexId, false);
    }
    catch (ProcessCanceledException ignored) {
    }
  }

  private void checkRebuild(@NotNull final ID<?, ?> indexId, final boolean cleanupOnly) {
    final AtomicInteger status = ourRebuildStatus.get(indexId);
    if (status.get() == OK) {
      return;
    }
    if (status.compareAndSet(REQUIRES_REBUILD, REBUILD_IN_PROGRESS)) {
      cleanupProcessedFlag();

      final Runnable rebuildRunnable = new Runnable() {
        @Override
        public void run() {
          try {
            clearIndex(indexId);
            if (!cleanupOnly) {
              scheduleIndexRebuild(false);
            }
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.info(e);
          }
          finally {
            status.compareAndSet(REBUILD_IN_PROGRESS, OK);
          }
        }
      };

      if (cleanupOnly || myIsUnitTestMode) {
        rebuildRunnable.run();
      }
      else {
        //noinspection SSBasedInspection
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            new Task.Modal(null, "Updating index", false) {
              @Override
              public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                rebuildRunnable.run();
              }
            }.queue();
          }
        }, ModalityState.NON_MODAL);
      }
    }

    if (status.get() == REBUILD_IN_PROGRESS) {
      throw new ProcessCanceledException();
    }
  }

  private void scheduleIndexRebuild(boolean forceDumbMode) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      final Set<CacheUpdater> updatersToRun = Collections.<CacheUpdater>singleton(new UnindexedFilesUpdater(project, this));
      final DumbServiceImpl service = DumbServiceImpl.getInstance(project);
      if (forceDumbMode) {
        service.queueCacheUpdateInDumbMode(updatersToRun);
      }
      else {
        service.queueCacheUpdate(updatersToRun);
      }
    }
  }

  private void clearIndex(@NotNull final ID<?, ?> indexId) throws StorageException {
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null : "Index with key " + indexId + " not found or not registered properly";
    index.clear();
    try {
      IndexInfrastructure.rewriteVersion(IndexInfrastructure.getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private Set<Document> getUnsavedOrTransactedDocuments() {
    final Set<Document> docs = new THashSet<Document>(Arrays.asList(myFileDocumentManager.getUnsavedDocuments()));
    synchronized (myTransactionMap) {
      docs.addAll(myTransactionMap.keySet());
    }
    return docs;
  }

  private void indexUnsavedDocuments(@NotNull ID<?, ?> indexId,
                                     @Nullable Project project,
                                     GlobalSearchScope filter,
                                     VirtualFile restrictedFile) throws StorageException {
    if (myUpToDateIndices.contains(indexId)) {
      return; // no need to index unsaved docs
    }

    final Set<Document> documents = getUnsavedOrTransactedDocuments();
    if (!documents.isEmpty()) {
      // now index unsaved data
      final StorageGuard.Holder guard = setDataBufferingEnabled(true);
      try {
        final Semaphore semaphore = myUnsavedDataIndexingSemaphores.get(indexId);

        assert semaphore != null : "Semaphore for unsaved data indexing was not initialized for index " + indexId;

        semaphore.down();
        boolean allDocsProcessed = true;
        try {
          for (Document document : documents) {
            allDocsProcessed &= indexUnsavedDocument(document, indexId, project, filter, restrictedFile);
          }
        }
        finally {
          semaphore.up();

          while (!semaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
            if (Thread.holdsLock(PsiLock.LOCK)) {
              break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
            }
          }
          if (allDocsProcessed && !hasActiveTransactions()) {
            myUpToDateIndices.add(indexId); // safe to set the flag here, because it will be cleared under the WriteAction
          }
        }
      }
      finally {
        guard.leave();
      }
    }
  }

  private boolean hasActiveTransactions() {
    synchronized (myTransactionMap) {
      return !myTransactionMap.isEmpty();
    }
  }

  private interface DocumentContent {
    String getText();

    long getModificationStamp();
  }

  private static class AuthenticContent implements DocumentContent {
    private final Document myDocument;

    private AuthenticContent(final Document document) {
      myDocument = document;
    }

    @Override
    public String getText() {
      return myDocument.getText();
    }

    @Override
    public long getModificationStamp() {
      return myDocument.getModificationStamp();
    }
  }

  private static class PsiContent implements DocumentContent {
    private final Document myDocument;
    private final PsiFile myFile;

    private PsiContent(final Document document, final PsiFile file) {
      myDocument = document;
      myFile = file;
    }

    @Override
    public String getText() {
      if (myFile.getViewProvider().getModificationStamp() != myDocument.getModificationStamp()) {
        final ASTNode node = myFile.getNode();
        assert node != null;
        return node.getText();
      }
      return myDocument.getText();
    }

    @Override
    public long getModificationStamp() {
      return myFile.getViewProvider().getModificationStamp();
    }
  }

  private static final Key<WeakReference<FileContentImpl>> ourFileContentKey = Key.create("unsaved.document.index.content");

  // returns false if doc was not indexed because the file does not fit in scope
  private boolean indexUnsavedDocument(@NotNull final Document document, @NotNull final ID<?, ?> requestedIndexId, final Project project,
                                       @Nullable GlobalSearchScope filter, @Nullable VirtualFile restrictedFile) throws StorageException {
    final VirtualFile vFile = myFileDocumentManager.getFile(document);
    if (!(vFile instanceof VirtualFileWithId) || !vFile.isValid()) {
      return true;
    }

    if (restrictedFile != null) {
      if (!Comparing.equal(vFile, restrictedFile)) {
        return false;
      }
    }
    else if (filter != null && !filter.accept(vFile)) {
      return false;
    }

    final PsiFile dominantContentFile = findDominantPsiForDocument(document, project);

    final DocumentContent content;
    if (dominantContentFile != null && dominantContentFile.getViewProvider().getModificationStamp() != document.getModificationStamp()) {
      content = new PsiContent(document, dominantContentFile);
    }
    else {
      content = new AuthenticContent(document);
    }

    final long currentDocStamp = content.getModificationStamp();
    if (currentDocStamp != myLastIndexedDocStamps.getAndSet(document, requestedIndexId, currentDocStamp)) {
      final Ref<StorageException> exRef = new Ref<StorageException>(null);
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        @Override
        public void run() {
          try {
            final String contentText = content.getText();
            if (isTooLarge(vFile, contentText.length())) {
              return;
            }

            // Reasonably attempt to use same file content when calculating indices as we can evaluate them several at once and store in file content
            WeakReference<FileContentImpl> previousContentRef = document.getUserData(ourFileContentKey);
            FileContentImpl previousContent = previousContentRef != null ? previousContentRef.get() : null;
            final FileContentImpl newFc;
            if (previousContent != null && previousContent.getStamp() == currentDocStamp) {
              newFc = previousContent;
            }
            else {
              newFc = new FileContentImpl(vFile, contentText, vFile.getCharset(), currentDocStamp);
              document.putUserData(ourFileContentKey, new WeakReference<FileContentImpl>(newFc));
            }

            if (dominantContentFile != null) {
              dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
              newFc.putUserData(IndexingDataKeys.PSI_FILE, dominantContentFile);
            }

            if (content instanceof AuthenticContent) {
              newFc.putUserData(EDITOR_HIGHLIGHTER, EditorHighlighterCache.getEditorHighlighterForCachesBuilding(document));
            }

            if (getInputFilter(requestedIndexId).acceptInput(vFile)) {
              newFc.putUserData(IndexingDataKeys.PROJECT, project);
              final int inputId = Math.abs(getFileId(vFile));
              getIndex(requestedIndexId).update(inputId, newFc);
            }

            if (dominantContentFile != null) {
              dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
            }
          }
          catch (StorageException e) {
            exRef.set(e);
          }
        }
      });
      final StorageException storageException = exRef.get();
      if (storageException != null) {
        throw storageException;
      }
    }
    return true;
  }

  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<EditorHighlighter>("Editor");

  @Nullable
  private PsiFile findDominantPsiForDocument(@NotNull Document document, @Nullable Project project) {
    synchronized (myTransactionMap) {
      PsiFile psiFile = myTransactionMap.get(document);
      if (psiFile != null) return psiFile;
    }

    return project == null ? null : findLatestKnownPsiForUncomittedDocument(document, project);
  }

  private final StorageGuard myStorageLock = new StorageGuard();

  @NotNull
  private StorageGuard.Holder setDataBufferingEnabled(final boolean enabled) {
    final StorageGuard.Holder holder = myStorageLock.enter(enabled);
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final IndexStorage indexStorage = index.getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
    return holder;
  }

  private void cleanupMemoryStorage() {
    myLastIndexedDocStamps.clear();
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final MemoryIndexStorage memStorage = (MemoryIndexStorage)index.getStorage();
      index.getWriteLock().lock();
      try {
        memStorage.clearMemoryMap();
      }
      finally {
        index.getWriteLock().unlock();
      }
      memStorage.fireMemoryStorageCleared();
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = readRegisteredIndexNames();
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }
    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  @Override
  public void requestRebuild(ID<?, ?> indexId, Throwable throwable) {
    cleanupProcessedFlag();
    LOG.info("Rebuild requested for index " + indexId, throwable);
    ourRebuildStatus.get(indexId).compareAndSet(OK, REQUIRES_REBUILD);
  }

  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    //noinspection unchecked
    return (UpdatableIndex<K, V, FileContent>)pair.getFirst();
  }

  private InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    return pair.getSecond();
  }

  public int getNumberOfPendingInvalidations() {
    return myChangedFilesCollector.getNumberOfPendingInvalidations();
  }

  @NotNull
  public Collection<VirtualFile> getFilesToUpdate(final Project project) {
    return ContainerUtil.findAll(myChangedFilesCollector.getAllFilesToUpdate(), new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile virtualFile) {
        for (IndexableFileSet set : myIndexableSets) {
          final Project proj = myIndexableSetToProjectMap.get(set);
          if (proj != null && !proj.equals(project)) {
            continue; // skip this set as associated with a different project
          }
          if (set.isInSet(virtualFile)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public void processRefreshedFile(@NotNull Project project, @NotNull final com.intellij.ide.caches.FileContent fileContent) {
    myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
    myChangedFilesCollector.processFileImpl(project, fileContent, false);
  }

  public void indexFileContent(@Nullable Project project, @NotNull com.intellij.ide.caches.FileContent content) {
    myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
    final VirtualFile file = content.getVirtualFile();

    FileTypeManagerImpl.cacheFileType(file, file.getFileType());
    try {
      PsiFile psiFile = null;
      FileContentImpl fc = null;
      for (final ID<?, ?> indexId : myIndices.keySet()) {
        if (shouldIndexFile(file, indexId)) {
          if (fc == null) {
            byte[] currentBytes;
            try {
              currentBytes = content.getBytes();
            }
            catch (IOException e) {
              currentBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
            }
            fc = new FileContentImpl(file, currentBytes);

            psiFile = content.getUserData(IndexingDataKeys.PSI_FILE);
            if (psiFile != null) {
              psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
              fc.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
            }
            if (project == null) {
              project = ProjectUtil.guessProjectForFile(file);
            }
            fc.putUserData(IndexingDataKeys.PROJECT, project);
          }

          try {
            ProgressManager.checkCanceled();
            updateSingleIndex(indexId, file, fc);
          }
          catch (ProcessCanceledException e) {
            myChangedFilesCollector.scheduleForUpdate(file);
            throw e;
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.info(e);
          }
        }
      }

      if (psiFile != null) {
        psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
      }
    }
    finally {
      FileTypeManagerImpl.cacheFileType(file, null);
    }
  }

  private void updateSingleIndex(final ID<?, ?> indexId, @NotNull final VirtualFile file, @Nullable final FileContent currentFC)
    throws StorageException {
    if (ourRebuildStatus.get(indexId).get() == REQUIRES_REBUILD) {
      return; // the index is scheduled for rebuild, no need to update
    }
    myLocalModCount++;

    final int inputId = Math.abs(getFileId(file));
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null;
    final Ref<StorageException> exRef = new Ref<StorageException>(null);

    final StorageGuard.Holder lock = setDataBufferingEnabled(false);
    try {
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        @Override
        public void run() {
          try {
            index.update(inputId, currentFC);
          }
          catch (StorageException e) {
            exRef.set(e);
          }
        }
      });
    }
    finally {
      lock.leave();
    }

    final StorageException storageException = exRef.get();
    if (storageException != null) {
      throw storageException;
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (file.isValid()) {
          if (currentFC != null) {
            IndexingStamp.update(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId));
          }
          else {
            // mark the file as unindexed
            IndexingStamp.update(file, indexId, IndexInfrastructure.INVALID_STAMP);
          }
        }
      }
    });
  }

  private boolean needsFileContentLoading(ID<?, ?> indexId) {
    return !myNotRequiringContentIndices.contains(indexId);
  }

  private abstract static class InvalidationTask implements Runnable {
    private final VirtualFile mySubj;

    protected InvalidationTask(final VirtualFile subj) {
      mySubj = subj;
    }

    public VirtualFile getSubj() {
      return mySubj;
    }
  }

  private static class SilentProgressIndicator extends DelegatingProgressIndicator {
    // suppress verbose messages

    private SilentProgressIndicator(ProgressIndicator indicator) {
      super(indicator);
    }

    @Nullable
    private static SilentProgressIndicator create() {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      return indicator != null ? new SilentProgressIndicator(indicator) : null;
    }

    @Override
    public void setText(String text) {
    }

    @Override
    public String getText() {
      return "";
    }

    @Override
    public void setText2(String text) {
    }

    @Override
    public String getText2() {
      return "";
    }
  }

  private final class ChangedFilesCollector extends VirtualFileAdapter {
    private final Set<VirtualFile> myFilesToUpdate = new ConcurrentHashSet<VirtualFile>();
    private final Queue<InvalidationTask> myFutureInvalidations = new ConcurrentLinkedQueue<InvalidationTask>();

    private final ManagingFS myManagingFS = ManagingFS.getInstance();
    // No need to react on movement events since files stay valid, their ids don't change and all associated attributes remain intact.

    @Override
    public void fileCreated(@NotNull final VirtualFileEvent event) {
      markDirty(event, false);
    }

    @Override
    public void fileDeleted(@NotNull final VirtualFileEvent event) {
      myFilesToUpdate.remove(event.getFile()); // no need to update it anymore
    }

    @Override
    public void fileCopied(@NotNull final VirtualFileCopyEvent event) {
      markDirty(event, false);
    }

    @Override
    public void beforeFileDeletion(@NotNull final VirtualFileEvent event) {
      invalidateIndices(event.getFile(), false);
    }

    @Override
    public void beforeContentsChange(@NotNull final VirtualFileEvent event) {
      invalidateIndices(event.getFile(), true);
    }

    @Override
    public void contentsChanged(@NotNull final VirtualFileEvent event) {
      markDirty(event, true);
    }

    @Override
    public void beforePropertyChange(@NotNull final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        final VirtualFile file = event.getFile();
        if (!file.isDirectory()) {
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          invalidateIndices(file, false);
        }
      }
    }

    @Override
    public void propertyChanged(@NotNull final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        if (!event.getFile().isDirectory()) {
          markDirty(event, false);
        }
      }
    }

    private void markDirty(@NotNull final VirtualFileEvent event, final boolean contentChange) {
      final VirtualFile eventFile = event.getFile();
      cleanProcessedFlag(eventFile);
      if (!contentChange) {
        myUpdatingFiles.incrementAndGet();
      }

      iterateIndexableFiles(eventFile, new Processor<VirtualFile>() {
        @Override
        public boolean process(@NotNull final VirtualFile file) {
          FileContent fileContent = null;
          // handle 'content-less' indices separately
          for (ID<?, ?> indexId : myNotRequiringContentIndices) {
            if (getInputFilter(indexId).acceptInput(file)) {
              try {
                if (fileContent == null) {
                  fileContent = new FileContentImpl(file);
                }
                updateSingleIndex(indexId, file, fileContent);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
          // For 'normal indices' schedule the file for update and stop iteration if at least one index accepts it
          if (!isTooLarge(file)) {
            for (ID<?, ?> indexId : myIndices.keySet()) {
              if (needsFileContentLoading(indexId) && getInputFilter(indexId).acceptInput(file)) {
                scheduleForUpdate(file);
                break; // no need to iterate further, as the file is already marked
              }
            }
          }

          return true;
        }
      });
      IndexingStamp.flushCache(null);
      if (!contentChange) {
        filesUpdateFinished();
      }
    }

    public void scheduleForUpdate(VirtualFile file) {
      myFilesToUpdate.add(file);
    }

    private void invalidateIndices(@NotNull final VirtualFile file, final boolean markForReindex) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (isUnderConfigOrSystem(file)) {
            return false;
          }
          if (file.isDirectory()) {
            if (!isMock(file) && !myManagingFS.wereChildrenAccessed(file)) {
              return false;
            }
          }
          else {
            invalidateIndicesForFile(file, markForReindex);
          }
          return true;
        }

        @Override
        public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
          return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : null;
        }
      });
    }

    private void invalidateIndicesForFile(final VirtualFile file, boolean markForReindex) {
      cleanProcessedFlag(file);
      IndexingStamp.flushCache(file);
      final List<ID<?, ?>> affectedIndices = new ArrayList<ID<?, ?>>(myIndices.size());

      for (final ID<?, ?> indexId : myIndices.keySet()) {
        try {
          if (!needsFileContentLoading(indexId)) {
            if (shouldUpdateIndex(file, indexId)) {
              updateSingleIndex(indexId, file, null);
            }
          }
          else { // the index requires file content
            if (shouldUpdateIndex(file, indexId)) {
              affectedIndices.add(indexId);
            }
          }
        }
        catch (StorageException e) {
          LOG.info(e);
          requestRebuild(indexId);
        }
      }

      if (!affectedIndices.isEmpty()) {
        if (markForReindex && !isTooLarge(file)) {
          // only mark the file as unindexed, reindex will be done lazily
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              for (ID<?, ?> indexId : affectedIndices) {
                IndexingStamp.update(file, indexId, IndexInfrastructure.INVALID_STAMP2);
              }
            }
          });
          // the file is for sure not a dir and it was previously indexed by at least one index
          scheduleForUpdate(file);
        }
        else {
          myFutureInvalidations.offer(new InvalidationTask(file) {
            @Override
            public void run() {
              removeFileDataFromIndices(affectedIndices, file);
            }
          });
        }
      }
      if (!markForReindex) {
        final boolean removedFromUpdateQueue = myFilesToUpdate.remove(file);// no need to update it anymore
        if (removedFromUpdateQueue && affectedIndices.isEmpty()) {
          // Currently the file is about to be deleted and previously it was scheduled for update and not processed up to now.
          // Because the file was scheduled for update, at the moment of scheduling it was marked as unindexed,
          // so, to be on the safe side, we have to schedule data invalidation from all content-requiring indices for this file
          myFutureInvalidations.offer(new InvalidationTask(file) {
            @Override
            public void run() {
              removeFileDataFromIndices(myRequiringContentIndices, file);
            }
          });
        }
      }

      IndexingStamp.flushCache(file);
    }

    private void removeFileDataFromIndices(@NotNull Collection<ID<?, ?>> affectedIndices, @NotNull VirtualFile file) {
      Throwable unexpectedError = null;
      for (ID<?, ?> indexId : affectedIndices) {
        try {
          updateSingleIndex(indexId, file, null);
        }
        catch (StorageException e) {
          LOG.info(e);
          requestRebuild(indexId);
        }
        catch (ProcessCanceledException ignored) {
        }
        catch (Throwable e) {
          LOG.info(e);
          if (unexpectedError == null) {
            unexpectedError = e;
          }
        }
      }
      IndexingStamp.flushCache(file);
      if (unexpectedError != null) {
        LOG.error(unexpectedError);
      }
    }

    public int getNumberOfPendingInvalidations() {
      return myFutureInvalidations.size();
    }

    public void ensureAllInvalidateTasksCompleted() {
      final int size = getNumberOfPendingInvalidations();
      if (size == 0) {
        return;
      }
      final ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
      final ProgressIndicator indicator = current != null ? current : new EmptyProgressIndicator();
      indicator.setText("");
      int count = 0;
      while (true) {
        InvalidationTask task = myFutureInvalidations.poll();

        if (task == null) {
          break;
        }
        indicator.setFraction((double)count++ / size);
        indicator.setText2(task.getSubj().getPresentableUrl());
        task.run();
      }
    }

    private void iterateIndexableFiles(@NotNull final VirtualFile file, @NotNull final Processor<VirtualFile> processor) {
      if (file.isDirectory()) {
        final ContentIterator iterator = new ContentIterator() {
          @Override
          public boolean processFile(@NotNull final VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              processor.process(fileOrDir);
            }
            return true;
          }
        };

        for (IndexableFileSet set : myIndexableSets) {
          if (set.isInSet(file)) {
            set.iterateIndexableFilesIn(file, iterator);
          }
        }
      }
      else {
        for (IndexableFileSet set : myIndexableSets) {
          if (set.isInSet(file)) {
            processor.process(file);
            break;
          }
        }
      }
    }

    public Collection<VirtualFile> getAllFilesToUpdate() {
      if (myFilesToUpdate.isEmpty()) {
        return Collections.emptyList();
      }
      return new ArrayList<VirtualFile>(myFilesToUpdate);
    }

    private final Semaphore myForceUpdateSemaphore = new Semaphore();

    private void forceUpdate(@Nullable Project project, @Nullable GlobalSearchScope filter, @Nullable VirtualFile restrictedTo,
                             boolean onlyRemoveOutdatedData) {
      myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
      for (VirtualFile file : getAllFilesToUpdate()) {
        if (filter == null || filter.accept(file) || Comparing.equal(file, restrictedTo)) {
          try {
            myForceUpdateSemaphore.down();
            // process only files that can affect result
            processFileImpl(project, new com.intellij.ide.caches.FileContent(file), onlyRemoveOutdatedData);
          }
          finally {
            myForceUpdateSemaphore.up();
          }
        }
      }

      // If several threads entered the method at the same time and there were files to update,
      // all the threads should leave the method synchronously after all the files scheduled for update are reindexed,
      // no matter which thread will do reindexing job.
      // Thus we ensure that all the threads that entered the method will get the most recent data

      while (!myForceUpdateSemaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
        if (Thread.holdsLock(PsiLock.LOCK)) {
          break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
        }
      }
    }

    private void processFileImpl(Project project,
                                 @NotNull final com.intellij.ide.caches.FileContent fileContent,
                                 boolean onlyRemoveOutdatedData) {
      final VirtualFile file = fileContent.getVirtualFile();
      final boolean reallyRemoved = myFilesToUpdate.remove(file);
      if (reallyRemoved && file.isValid()) {
        if (onlyRemoveOutdatedData) {
          // on shutdown there is no need to re-index the file, just remove outdated data from indices
          final List<ID<?, ?>> affected = new ArrayList<ID<?, ?>>();
          for (final ID<?, ?> indexId : myRequiringContentIndices) {  // non requiring content indices should be flushed
            if (getInputFilter(indexId).acceptInput(file)) {
              affected.add(indexId);
            }
          }
          removeFileDataFromIndices(affected, file);
        }
        else {
          indexFileContent(project, fileContent);
        }
        IndexingStamp.flushCache(file);
      }
    }
  }

  private class UnindexedFilesFinder implements CollectingContentIterator {
    private final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    @Nullable
    private final ProgressIndicator myProgressIndicator;

    private UnindexedFilesFinder(@Nullable ProgressIndicator indicator) {
      myProgressIndicator = indicator;
    }

    @NotNull
    @Override
    public List<VirtualFile> getFiles() {
      return myFiles;
    }

    @Override
    public boolean processFile(@NotNull final VirtualFile file) {
      if (!file.isValid()) {
        return true;
      }
      if (!file.isDirectory()) {
        if (file instanceof NewVirtualFile && ((NewVirtualFile)file).getFlag(ALREADY_PROCESSED)) {
          return true;
        }

        if (file instanceof VirtualFileWithId) {
          try {
            FileTypeManagerImpl.cacheFileType(file, file.getFileType());

            boolean oldStuff = true;
            if (!isTooLarge(file)) {
              for (ID<?, ?> indexId : myIndices.keySet()) {
                try {
                  if (needsFileContentLoading(indexId) && shouldIndexFile(file, indexId)) {
                    myFiles.add(file);
                    oldStuff = false;
                    break;
                  }
                }
                catch (RuntimeException e) {
                  final Throwable cause = e.getCause();
                  if (cause instanceof IOException || cause instanceof StorageException) {
                    LOG.info(e);
                    requestRebuild(indexId);
                  }
                  else {
                    throw e;
                  }
                }
              }
            }
            FileContent fileContent = null;
            for (ID<?, ?> indexId : myNotRequiringContentIndices) {
              if (shouldIndexFile(file, indexId)) {
                oldStuff = false;
                try {
                  if (fileContent == null) {
                    fileContent = new FileContentImpl(file);
                  }
                  updateSingleIndex(indexId, file, fileContent);
                }
                catch (StorageException e) {
                  LOG.info(e);
                  requestRebuild(indexId);
                }
              }
            }
            IndexingStamp.flushCache(file);

            if (oldStuff && file instanceof NewVirtualFile) {
              ((NewVirtualFile)file).setFlag(ALREADY_PROCESSED, true);
            }
          }
          finally {
            FileTypeManagerImpl.cacheFileType(file, null);
          }
        }
      }
      else {
        if (myProgressIndicator != null) { // once for dir is cheap enough
          myProgressIndicator.checkCanceled();
          myProgressIndicator.setText("Scanning files to index");
          myProgressIndicator.setText2(file.getPresentableUrl());
        }
      }
      return true;
    }
  }

  private boolean shouldUpdateIndex(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private boolean shouldIndexFile(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || !IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private boolean isUnderConfigOrSystem(@NotNull VirtualFile file) {
    final String filePath = file.getPath();
    return myConfigPath != null && FileUtil.startsWith(filePath, myConfigPath) ||
           myLogPath != null && FileUtil.startsWith(filePath, myLogPath);
  }

  private static boolean isMock(final VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  private boolean isTooLarge(@NotNull VirtualFile file) {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
      final FileType type = file.getFileType();
      return !myNoLimitCheckTypes.contains(type);
    }
    return false;
  }

  private boolean isTooLarge(@NotNull VirtualFile file, long contentSize) {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file, contentSize)) {
      final FileType type = file.getFileType();
      return !myNoLimitCheckTypes.contains(type);
    }
    return false;
  }

  @NotNull
  public CollectingContentIterator createContentIterator(@Nullable ProgressIndicator indicator) {
    myUpdatingFiles.incrementAndGet();
    return new UnindexedFilesFinder(indicator);
  }

  @Override
  public void registerIndexableSet(@NotNull IndexableFileSet set, @Nullable Project project) {
    myIndexableSets.add(set);
    myIndexableSetToProjectMap.put(set, project);
  }

  @Override
  public void removeIndexableSet(@NotNull IndexableFileSet set) {
    myChangedFilesCollector.forceUpdate(null, null, null, true);
    IndexingStamp.flushCache(null);
    myIndexableSets.remove(set);
    myIndexableSetToProjectMap.remove(set);
  }

  @Nullable
  private static PsiFile findLatestKnownPsiForUncomittedDocument(@NotNull Document doc, @NotNull Project project) {
    return PsiDocumentManager.getInstance(project).getCachedPsiFile(doc);
  }

  private static class IndexableFilesFilter implements InputFilter {
    private final InputFilter myDelegate;

    private IndexableFilesFilter(InputFilter delegate) {
      myDelegate = delegate;
    }

    @Override
    public boolean acceptInput(final VirtualFile file) {
      return file instanceof VirtualFileWithId && myDelegate.acceptInput(file);
    }
  }

  private static void cleanupProcessedFlag() {
    final VirtualFile[] roots = ManagingFS.getInstance().getRoots();
    for (VirtualFile root : roots) {
      cleanProcessedFlag(root);
    }
  }

  private static void cleanProcessedFlag(@NotNull final VirtualFile file) {
    if (!(file instanceof NewVirtualFile)) return;

    final NewVirtualFile nvf = (NewVirtualFile)file;
    if (file.isDirectory()) {
      for (VirtualFile child : nvf.getCachedChildren()) {
        cleanProcessedFlag(child);
      }
    }
    else {
      nvf.setFlag(ALREADY_PROCESSED, false);
    }
  }

  @Override
  public void iterateIndexableFiles(@NotNull final ContentIterator processor, @NotNull Project project, ProgressIndicator indicator) {
    if (project.isDisposed()) {
      return;
    }
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    // iterate project content
    projectFileIndex.iterateContent(processor);

    if (project.isDisposed()) {
      return;
    }

    Set<VirtualFile> visitedRoots = new THashSet<VirtualFile>();
    for (IndexedRootsProvider provider : Extensions.getExtensions(IndexedRootsProvider.EP_NAME)) {
      //important not to depend on project here, to support per-project background reindex
      // each client gives a project to FileBasedIndex
      if (project.isDisposed()) {
        return;
      }
      for (VirtualFile root : IndexableSetContributor.getRootsToIndex(provider)) {
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
      for (VirtualFile root : IndexableSetContributor.getProjectRootsToIndex(provider, project)) {
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
    }

    if (project.isDisposed()) {
      return;
    }
    // iterate associated libraries
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (module.isDisposed()) {
        return;
      }
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          if (orderEntry.isValid()) {
            final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
            final VirtualFile[] libSources = entry.getRootFiles(OrderRootType.SOURCES);
            final VirtualFile[] libClasses = entry.getRootFiles(OrderRootType.CLASSES);
            for (VirtualFile[] roots : new VirtualFile[][]{libSources, libClasses}) {
              for (VirtualFile root : roots) {
                if (visitedRoots.add(root)) {
                  iterateRecursively(root, processor, indicator);
                }
              }
            }
          }
        }
      }
    }
  }

  private static void iterateRecursively(@Nullable final VirtualFile root,
                                         @NotNull final ContentIterator processor,
                                         @Nullable final ProgressIndicator indicator) {
    if (root == null) {
      return;
    }

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (indicator != null) indicator.checkCanceled();

        if (!file.isDirectory()) {
          processor.processFile(file);
        }
        else if (indicator != null) {
          // once for directory should be cheap enough
          indicator.setText2(file.getPresentableUrl());
        }
        return true;
      }
    });
  }

  @SuppressWarnings({"WhileLoopSpinsOnField", "SynchronizeOnThis"})
  private static class StorageGuard {
    private int myHolds = 0;

    public interface Holder {
      void leave();
    }

    private final Holder myTrueHolder = new Holder() {
      @Override
      public void leave() {
        StorageGuard.this.leave(true);
      }
    };
    private final Holder myFalseHolder = new Holder() {
      @Override
      public void leave() {
        StorageGuard.this.leave(false);
      }
    };

    @NotNull
    public synchronized Holder enter(boolean mode) {
      if (mode) {
        while (myHolds < 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds++;
        return myTrueHolder;
      }
      else {
        while (myHolds > 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds--;
        return myFalseHolder;
      }
    }

    private synchronized void leave(boolean mode) {
      myHolds += mode ? -1 : 1;
      if (myHolds == 0) {
        notifyAll();
      }
    }
  }
}
