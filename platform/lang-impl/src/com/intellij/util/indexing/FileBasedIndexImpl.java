/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.ASTNode;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.EditorHighlighterCache;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.SerializationManagerEx;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.*;
import jsr166e.extra.SequenceLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
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
  private static final String CORRUPTION_MARKER_NAME = "corruption.marker";
  private static final NotificationGroup NOTIFICATIONS = new NotificationGroup("Indexing", NotificationDisplayType.BALLOON, false);

  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices =
    new THashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final List<ID<?, ?>> myIndicesWithoutFileTypeInfo = new ArrayList<ID<?, ?>>();
  private final Map<FileType, List<ID<?, ?>>> myFileType2IndicesWithFileTypeInfoMap = new THashMap<FileType, List<ID<?, ?>>>();
  private final List<ID<?, ?>> myIndicesForDirectories = new SmartList<ID<?, ?>>();

  private final Map<ID<?, ?>, DocumentUpdateTask> myUnsavedDataUpdateTasks = new THashMap<ID<?, ?>, DocumentUpdateTask>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNotRequiringContentIndices = new THashSet<ID<?, ?>>();
  private final Set<ID<?, ?>> myRequiringContentIndices = new THashSet<ID<?, ?>>();
  private final Set<ID<?, ?>> myPsiDependentIndices = new THashSet<ID<?, ?>>();
  private final Set<FileType> myNoLimitCheckTypes = new THashSet<FileType>();

  private final PerIndexDocumentVersionMap myLastIndexedDocStamps = new PerIndexDocumentVersionMap();
  @NotNull private final ChangedFilesCollector myChangedFilesCollector;

  private final List<IndexableFileSet> myIndexableSets = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<IndexableFileSet, Project> myIndexableSetToProjectMap = new THashMap<IndexableFileSet, Project>();

  private static final int OK = 1;
  private static final int REQUIRES_REBUILD = 2;
  private static final Map<ID<?, ?>, AtomicInteger> ourRebuildStatus = new THashMap<ID<?, ?>, AtomicInteger>();

  private final MessageBusConnection myConnection;
  private final FileDocumentManager myFileDocumentManager;
  private final FileTypeManagerImpl myFileTypeManager;
  private final SerializationManagerEx mySerializationManagerEx;
  private final Set<ID<?, ?>> myUpToDateIndicesForUnsavedOrTransactedDocuments = ContainerUtil.newConcurrentSet();
  private volatile SmartFMap<Document, PsiFile> myTransactionMap = SmartFMap.emptyMap();

  @Nullable private final String myConfigPath;
  @Nullable private final String myLogPath;
  private final boolean myIsUnitTestMode;
  @Nullable private ScheduledFuture<?> myFlushingFuture;
  private volatile int myLocalModCount;
  private volatile int myFilesModCount;
  private final AtomicInteger myUpdatingFiles = new AtomicInteger();
  private final Set<Project> myProjectsBeingUpdated = ContainerUtil.newConcurrentSet();
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();

  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"}) private volatile boolean myInitialized;
    // need this variable for memory barrier

  public FileBasedIndexImpl(@SuppressWarnings("UnusedParameters") VirtualFileManager vfManager,
                            FileDocumentManager fdm,
                            FileTypeManagerImpl fileTypeManager,
                            @NotNull MessageBus bus,
                            SerializationManagerEx sm) {
    myFileDocumentManager = fdm;
    myFileTypeManager = fileTypeManager;
    mySerializationManagerEx = sm;
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    myConfigPath = calcConfigPath(PathManager.getConfigPath());
    myLogPath = calcConfigPath(PathManager.getLogPath());

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(@NotNull final Document doc, @NotNull final PsiFile file) {
        myTransactionMap = myTransactionMap.plus(doc, file);
        myUpToDateIndicesForUnsavedOrTransactedDocuments.clear();
      }

      @Override
      public void transactionCompleted(@NotNull final Document doc, @NotNull final PsiFile file) {
        myTransactionMap = myTransactionMap.minus(doc);
      }
    });

    connection.subscribe(FileTypeManager.TOPIC, new FileTypeListener() {
      @Nullable private Map<FileType, Set<String>> myTypeToExtensionMap;

      @Override
      public void beforeFileTypesChanged(@NotNull final FileTypeEvent event) {
        cleanupProcessedFlag();
        myTypeToExtensionMap = new THashMap<FileType, Set<String>>();
        for (FileType type : myFileTypeManager.getRegisteredFileTypes()) {
          myTypeToExtensionMap.put(type, getExtensions(type));
        }
      }

      @Override
      public void fileTypesChanged(@NotNull final FileTypeEvent event) {
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
        IndexingStamp.flushCaches();
        for (ID<?, ?> indexId : myIndices.keySet()) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            LOG.info(e);
          }
        }
        scheduleIndexRebuild("File type change");
      }
    });

    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
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
        myUpToDateIndicesForUnsavedOrTransactedDocuments.clear();
      }
    });

    myChangedFilesCollector = new ChangedFilesCollector();
    myConnection = connection;
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file, @Nullable FileType fileType) {
    return ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType);
  }

  static boolean belongsToScope(VirtualFile file, VirtualFile restrictedTo, GlobalSearchScope filter) {
    if (!(file instanceof VirtualFileWithId) || !file.isValid()) {
      return false;
    }

    if (restrictedTo != null && !Comparing.equal(file, restrictedTo) ||
        filter != null && restrictedTo == null && !filter.accept(file)
      ) {
      return false;
    }
    return true;
  }

  @Override
  public void requestReindex(@NotNull final VirtualFile file) {
    myChangedFilesCollector.invalidateIndicesRecursively(file, true);
  }

  private void initExtensions() {
    try {
      File indexRoot = PathManager.getIndexRoot();
      final File corruptionMarker = new File(indexRoot, CORRUPTION_MARKER_NAME);
      final boolean currentVersionCorrupted = corruptionMarker.exists();
      if (currentVersionCorrupted) {
        FileUtil.deleteWithRenaming(indexRoot);
        indexRoot.mkdirs();
        // serialization manager is initialized before and use removed index root so we need to reinitialize it
        mySerializationManagerEx.reinitializeNameStorage();
      }

      FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);

      boolean versionChanged = false;
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        try {
          ourRebuildStatus.put(extension.getName(), new AtomicInteger(OK));
          versionChanged |= registerIndexer(extension);
        }
        catch (IOException e) {
          throw e;
        }
        catch (Throwable t) {
          PluginManager.handleComponentError(t, extension.getClass().getName(), null);
        }
      }

      for (List<ID<?, ?>> value : myFileType2IndicesWithFileTypeInfoMap.values()) {
        value.addAll(myIndicesWithoutFileTypeInfo);
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
        NOTIFICATIONS.createNotification("Index Rebuild", rebuildNotification, NotificationType.INFORMATION, null).notify(null);
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

      myConnection.subscribe(VirtualFileManager.VFS_CHANGES, myChangedFilesCollector);

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
        private int lastModCount;

        @Override
        public void run() {
          mySerializationManagerEx.flushNameStorage();

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
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   */
  private <K, V> boolean registerIndexer(@NotNull final FileBasedIndexExtension<K, V> extension) throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    final boolean versionFileExisted = versionFile.exists();
    boolean versionChanged = false;
    if (IndexingStamp.versionDiffers(versionFile, version)) {
      if (versionFileExisted) {
        versionChanged = true;
        LOG.info("Version has changed for index " + name + ". The index will be rebuilt.");
      }
      if (extension.hasSnapshotMapping() && versionChanged) {
        FileUtil.deleteWithRenaming(IndexInfrastructure.getPersistentIndexRootDir(name));
      }
      File rootDir = IndexInfrastructure.getIndexRootDir(name);
      if (versionFileExisted) FileUtil.deleteWithRenaming(rootDir);
      IndexingStamp.rewriteVersion(versionFile, version);
    }

    initIndexStorage(extension, version, versionFile);

    return versionChanged;
  }

  private <K, V> void initIndexStorage(@NotNull FileBasedIndexExtension<K, V> extension, int version, @NotNull File versionFile)
    throws IOException {
    MapIndexStorage<K, V> storage = null;
    final ID<K, V> name = extension.getName();
    boolean contentHashesEnumeratorOk = false;

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (extension.hasSnapshotMapping()) {
          ContentHashesSupport.initContentHashesEnumerator();
          contentHashesEnumeratorOk = true;
        }
        storage = new MapIndexStorage<K, V>(
          IndexInfrastructure.getStorageFile(name),
          extension.getKeyDescriptor(),
          extension.getValueExternalizer(),
          extension.getCacheSize(),
          extension.keyIsUniqueForIndexedFile(),
          extension.traceKeyHashToVirtualFileMapping()
        );

        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(name, extension, memStorage);
        final InputFilter inputFilter = extension.getInputFilter();

        myIndices.put(name, new Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>(index, new IndexableFilesFilter(inputFilter)));
        if (inputFilter instanceof FileTypeSpecificInputFilter) {
          ((FileTypeSpecificInputFilter)inputFilter).registerFileTypesUsedForIndexing(new Consumer<FileType>() {
            final Set<FileType> addedTypes = new THashSet<FileType>();
            @Override
            public void consume(FileType type) {
              if (type == null || !addedTypes.add(type)) {
                return;
              }
              List<ID<?, ?>> ids = myFileType2IndicesWithFileTypeInfoMap.get(type);
              if (ids == null) myFileType2IndicesWithFileTypeInfoMap.put(type, ids = new ArrayList<ID<?, ?>>(5));
              ids.add(name);
            }
          });
        }
        else {
          myIndicesWithoutFileTypeInfo.add(name);
        }

        myUnsavedDataUpdateTasks.put(name, new DocumentUpdateTask(name));
        myIndexIdToVersionMap.put(name, version);
        if (!extension.dependsOnFileContent()) {
          if (extension.indexDirectories()) myIndicesForDirectories.add(name);
          myNotRequiringContentIndices.add(name);
        }
        else {
          myRequiringContentIndices.add(name);
        }
        if (extension instanceof PsiDependentIndex) myPsiDependentIndices.add(name);
        myNoLimitCheckTypes.addAll(extension.getFileTypesWithSizeLimitNotApplicable());
        break;
      }
      catch (Exception e) {
        LOG.info(e);
        boolean instantiatedStorage = storage != null;
        try {
          if (storage != null) storage.close();
          storage = null;
        }
        catch (Exception ignored) {
        }

        FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(name));

        if (extension.hasSnapshotMapping() && (!contentHashesEnumeratorOk || instantiatedStorage)) {
          FileUtil.deleteWithRenaming(IndexInfrastructure.getPersistentIndexRootDir(name)); // todo there is possibility of corruption of storage and content hashes
        }
        IndexingStamp.rewriteVersion(versionFile, version);
      }
    }
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
      DataExternalizer<Collection<K>> externalizer =
        extension.hasSnapshotMapping() && IdIndex.ourSnapshotMappingsEnabled
        ? createInputsIndexExternalizer(extension, indexId, extension.getKeyDescriptor())
        : null;
      index = new MapReduceIndex<K, V, FileContent>(
        indexId, extension.getIndexer(), storage, externalizer, extension.getValueExternalizer(), extension instanceof PsiDependentIndex);
    }
    index.setInputIdToDataKeysIndex(new Factory<PersistentHashMap<Integer, Collection<K>>>() {
      @Override
      public PersistentHashMap<Integer, Collection<K>> create() {
        try {
          return createIdToDataKeysIndex(extension, storage);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    return index;
  }

  @NotNull
  public static <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(@NotNull FileBasedIndexExtension <K, ?> extension,
                                                                                      @NotNull MemoryIndexStorage<K, ?> storage)
    throws IOException {
    ID<K, ?> indexId = extension.getName();
    KeyDescriptor<K> keyDescriptor = extension.getKeyDescriptor();
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);
    final AtomicBoolean isBufferingMode = new AtomicBoolean();
    final TIntObjectHashMap<Collection<K>> tempMap = new TIntObjectHashMap<Collection<K>>();

    // Important! Update IdToDataKeysIndex depending on the sate of "buffering" flag from the MemoryStorage.
    // If buffering is on, all changes should be done in memory (similar to the way it is done in memory storage).
    // Otherwise data in IdToDataKeysIndex will not be in sync with the 'main' data in the index on disk and index updates will be based on the
    // wrong sets of keys for the given file. This will lead to unpredictable results in main index because it will not be
    // cleared properly before updating (removed data will still be present on disk). See IDEA-52223 for illustration of possible effects.

    final PersistentHashMap<Integer, Collection<K>> map = new PersistentHashMap<Integer, Collection<K>>(
      indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, createInputsIndexExternalizer(extension, indexId, keyDescriptor)
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

    storage.addBufferingStateListener(new MemoryIndexStorage.BufferingStateListener() {
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

  private static <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(FileBasedIndexExtension<K, ?> extension,
                                                                                  ID<K, ?> indexId,
                                                                                  KeyDescriptor<K> keyDescriptor) {
    DataExternalizer<Collection<K>> externalizer;
    if (extension instanceof CustomInputsIndexFileBasedIndexExtension) {
      externalizer = ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer();
    } else {
      externalizer = new InputIndexDataExternalizer<K>(keyDescriptor, indexId);
    }
    return externalizer;
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
        for(VirtualFile file:myChangedFilesCollector.getAllFilesToUpdate()) {
          if (!file.isValid()) {
            removeDataFromIndicesForFile(Math.abs(getIdMaskingNonIdBasedFile(file)));
          }
        }
        IndexingStamp.flushCaches();

        for (ID<?, ?> indexId : myIndices.keySet()) {
          final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
          assert index != null;
          if(ourRebuildStatus.get(indexId).get() != OK) {
            doClearIndex(indexId); // if the index was scheduled for rebuild, only clean it
          }
          index.dispose();
        }

        ContentHashesSupport.flushContentHashes();
        myConnection.disconnect();
      }
      catch (Throwable e) {
        LOG.error("Problems during index shutdown", e);
      }
      LOG.info("END INDEX SHUTDOWN");
    }
  }

  private void removeDataFromIndicesForFile(final int fileId) {
    // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
    final List<ID<?, ?>> currentFileIndexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);
    Collection<ID<?, ?>> states = currentFileIndexedStates;
    for(ID<?,?> currentFileIndexedState: currentFileIndexedStates) {
      if (!myIndices.containsKey(currentFileIndexedState)) {
        states = ContainerUtil.intersection(currentFileIndexedStates, myIndices.keySet());
        break;
      }
    }

    if (!states.isEmpty()) {
      final Collection<ID<?, ?>> finalStates = states;
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        @Override
        public void run() {
          removeFileDataFromIndices(finalStates, fileId);
        }
      });
    }
  }

  private void removeFileDataFromIndices(@NotNull Collection<ID<?, ?>> affectedIndices, int inputId) {
    Throwable unexpectedError = null;
    for (ID<?, ?> indexId : affectedIndices) {
      try {
        updateSingleIndex(indexId, inputId, null);
      }
      catch (StorageException e) {
        LOG.info(e);
        requestRebuild(indexId);
      }
      catch (ProcessCanceledException pce) {
        LOG.error(pce);
      }
      catch (Throwable e) {
        LOG.info(e);
        if (unexpectedError == null) {
          unexpectedError = e;
        }
      }
    }
    IndexingStamp.flushCache(inputId);
    if (unexpectedError != null) {
      LOG.error(unexpectedError);
    }
  }

  private void flushAllIndices(final long modCount) {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      return;
    }
    IndexingStamp.flushCaches();
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

    ContentHashesSupport.flushContentHashes();
  }

  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull final ID<K, ?> indexId, @NotNull Project project) {
    Set<K> allKeys = new THashSet<K>();
    processAllKeys(indexId, new CommonProcessors.CollectProcessor<K>(allKeys), project);
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull final ID<K, ?> indexId, @NotNull Processor<K> processor, @Nullable Project project) {
    return processAllKeys(indexId, processor, project == null ? new EverythingGlobalScope() : GlobalSearchScope.allScope(project), null);
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    try {
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (index == null) {
        return true;
      }
      ensureUpToDate(indexId, scope.getProject(), scope);
      return index.processAllKeys(processor, scope, idFilter);
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
    ProgressManager.checkCanceled();
    myContentlessIndicesUpdateQueue.ensureUpToDate(); // some contentful indices depends on contentless ones
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!needsFileContentLoading(indexId)) {
      return; //indexed eagerly in foreground while building unindexed file list
    }
    if (filter == GlobalSearchScope.EMPTY_SCOPE) {
      return;
    }
    if (ActionUtil.isDumbMode(project)) {
      handleDumbMode(project);
    }

    if (myReentrancyGuard.get().booleanValue()) {
      //assert false : "ensureUpToDate() is not reentrant!";
      return;
    }
    myReentrancyGuard.set(Boolean.TRUE);

    try {
      if (isUpToDateCheckEnabled()) {
        try {
          if (ourRebuildStatus.get(indexId).get() != OK) {
            throw new ProcessCanceledException();
          }
          forceUpdate(project, filter, restrictedFile);
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

    throw new IndexNotReadyException(project == null ? null : DumbServiceImpl.getInstance(project).getDumbModeStartTrace());
  }

  @Override
  @NotNull
  public <K, V> List<V> getValues(@NotNull final ID<K, V> indexId, @NotNull K dataKey, @NotNull final GlobalSearchScope filter) {
    final List<V> values = new SmartList<V>();
    VirtualFile restrictToFile = null;

    if (filter instanceof Iterable) {
      final Iterator<VirtualFile> virtualFileIterator = ((Iterable<VirtualFile>)filter).iterator();

      if (virtualFileIterator.hasNext()) {
        VirtualFile restrictToFileCandidate = virtualFileIterator.next();

        if (!virtualFileIterator.hasNext()) {
          restrictToFile = restrictToFileCandidate;
        }
      }
    }

    processValuesImpl(indexId, dataKey, true, restrictToFile, new ValueProcessor<V>() {
      @Override
      public boolean process(final VirtualFile file, final V value) {
        values.add(value);
        return true;
      }
    }, filter, null);
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
    }, filter, null);
    return files;
  }


  @Override
  public <K, V> boolean processValues(@NotNull final ID<K, V> indexId, @NotNull final K dataKey, @Nullable final VirtualFile inFile,
                                      @NotNull ValueProcessor<V> processor, @NotNull final GlobalSearchScope filter) {
    return processValues(indexId, dataKey, inFile, processor, filter, null);
  }

  @Override
  public <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                      @NotNull K dataKey,
                                      @Nullable VirtualFile inFile,
                                      @NotNull ValueProcessor<V> processor,
                                      @NotNull GlobalSearchScope filter,
                                      @Nullable IdFilter idFilter) {
    return processValuesImpl(indexId, dataKey, false, inFile, processor, filter, idFilter);
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

      myAccessValidator.checkAccessingIndexDuringOtherIndexProcessing(indexId);

      try {
        index.getReadLock().lock();
        myAccessValidator.startedProcessingActivityForIndex(indexId);
        return computable.convert(index);
      }
      finally {
        myAccessValidator.stoppedProcessingActivityForIndex(indexId);
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
    } catch(AssertionError ae) {
      scheduleRebuild(indexId, ae);
    }
    return null;
  }

  private <K, V> boolean processValuesImpl(@NotNull final ID<K, V> indexId, @NotNull final K dataKey, final boolean ensureValueProcessedOnce,
                                           @Nullable final VirtualFile restrictToFile, @NotNull final ValueProcessor<V> processor,
                                           @NotNull final GlobalSearchScope scope, @Nullable final IdFilter idFilter) {
    ThrowableConvertor<UpdatableIndex<K, V, FileContent>, Boolean, StorageException> keyProcessor =
      new ThrowableConvertor<UpdatableIndex<K, V, FileContent>, Boolean, StorageException>() {
        @Override
        public Boolean convert(@NotNull UpdatableIndex<K, V, FileContent> index) throws StorageException {
          final ValueContainer<V> container = index.getData(dataKey);

          boolean shouldContinue = true;

          if (restrictToFile != null) {
            if (restrictToFile instanceof VirtualFileWithId) {
              final int restrictedFileId = getFileId(restrictToFile);
              for (final ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
                final V value = valueIt.next();
                if (valueIt.getValueAssociationPredicate().contains(restrictedFileId)) {
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
            final IdFilter filter = idFilter != null ? idFilter : projectIndexableFiles(scope.getProject());
            VALUES_LOOP:
            for (final ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
              final V value = valueIt.next();
              for (final ValueContainer.IntIterator inputIdsIterator = valueIt.getInputIdsIterator(); inputIdsIterator.hasNext(); ) {
                final int id = inputIdsIterator.next();
                if (filter != null && !filter.containsFileId(id)) continue;
                VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
                if (file != null && scope.accept(file)) {
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
    final Boolean result = processExceptions(indexId, restrictToFile, scope, keyProcessor);
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

  public void filesUpdateEnumerationFinished() {
    myContentlessIndicesUpdateQueue.ensureUpToDate();
    myContentlessIndicesUpdateQueue.signalUpdateEnd();
  }

  @TestOnly
  public void cleanupForNextTest() {
    myTransactionMap = SmartFMap.emptyMap();
  }

  public static final class ProjectIndexableFilesFilter extends IdFilter {
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
          if (value < 0) value = -value;
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
          if (value < 0) value = -value;
          value -= myMinId;
          myBitMask[value >> SHIFT] |= (1L << (value & MASK));
          return true;
        }
      });
    }

    @Override
    public boolean containsFileId(int id) {
      if (id < myMinId) return false;
      if (id > myMaxId) return false;
      id -= myMinId;
      return (myBitMask[id >> SHIFT] & (1L << (id & MASK))) != 0;
    }
  }

  void filesUpdateStarted(Project project) {
    myContentlessIndicesUpdateQueue.signalUpdateStart();
    myContentlessIndicesUpdateQueue.ensureUpToDate();
    myProjectsBeingUpdated.add(project);
    ++myFilesModCount;
  }

  void filesUpdateFinished(@NotNull Project project) {
    myProjectsBeingUpdated.remove(project);
    ++myFilesModCount;
  }

  private final Lock myCalcIndexableFilesLock = new SequenceLock();

  @Nullable
  public ProjectIndexableFilesFilter projectIndexableFiles(@Nullable Project project) {
    if (project == null || myUpdatingFiles.get() > 0) return null;
    if (myProjectsBeingUpdated.contains(project)) return null;

    SoftReference<ProjectIndexableFilesFilter> reference = project.getUserData(ourProjectFilesSetKey);
    ProjectIndexableFilesFilter data = com.intellij.reference.SoftReference.dereference(reference);
    if (data != null && data.myModificationCount == myFilesModCount) return data;

    if (myCalcIndexableFilesLock.tryLock()) { // make best effort for calculating filter
      try {
        reference = project.getUserData(ourProjectFilesSetKey);
        data = com.intellij.reference.SoftReference.dereference(reference);
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

            for (final ValueContainer.ValueIterator<V> valueIt = container.getValueIterator(); valueIt.hasNext(); ) {
              final V value = valueIt.next();
              if (valueChecker != null && !valueChecker.value(value)) {
                continue;
              }

              ValueContainer.IntIterator iterator = valueIt.getInputIdsIterator();

              if (mainIntersection == null || iterator.size() < mainIntersection.size()) {
                while (iterator.hasNext()) {
                  final int id = iterator.next();
                  if (mainIntersection == null && (projectFilesFilter == null || projectFilesFilter.containsFileId(id)) ||
                      mainIntersection != null && mainIntersection.contains(id)
                    ) {
                    copy.add(id);
                  }
                }
              }
              else {
                mainIntersection.forEach(new TIntProcedure() {
                  final ValueContainer.IntPredicate predicate = valueIt.getValueAssociationPredicate();

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
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // avoid rebuilding index in tests since we do it synchronously in requestRebuild and we can have readAction at hand
      return null;
    }
    if (e instanceof IndexOutOfBoundsException) return e; // something wrong with direct byte buffer
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
    return processFilesContainingAllKeys(indexId, dataKeys, filter, null, processor);
  }

  @Override
  public <K> void scheduleRebuild(@NotNull final ID<K, ?> indexId, @NotNull final Throwable e) {
    requestRebuild(indexId, new Throwable(e));
  }

  private static void scheduleIndexRebuild(String reason) {
    LOG.info("scheduleIndexRebuild, reason: " + reason);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DumbService.getInstance(project).queueTask(new UnindexedFilesUpdater(project, false));
    }
  }

  private void clearIndex(@NotNull final ID<?, ?> indexId) throws StorageException {
    advanceIndexVersion(indexId);
    doClearIndex(indexId);
  }

  private void doClearIndex(ID<?, ?> indexId) throws StorageException {
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null : "Index with key " + indexId + " not found or not registered properly";
    index.clear();
  }

  private void advanceIndexVersion(ID<?, ?> indexId) {
    try {
      IndexingStamp.rewriteVersion(IndexInfrastructure.getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private Set<Document> getUnsavedDocuments() {
    Document[] documents = myFileDocumentManager.getUnsavedDocuments();
    if (documents.length == 0) return Collections.emptySet();
    if (documents.length == 1) return Collections.singleton(documents[0]);
    return new THashSet<Document>(Arrays.asList(documents));
  }

  @NotNull
  private Set<Document> getTransactedDocuments() {
    return myTransactionMap.keySet();
  }

  private void indexUnsavedDocuments(@NotNull final ID<?, ?> indexId,
                                     @Nullable Project project,
                                     final GlobalSearchScope filter,
                                     final VirtualFile restrictedFile) throws StorageException {
    if (myUpToDateIndicesForUnsavedOrTransactedDocuments.contains(indexId)) {
      return; // no need to index unsaved docs
    }

    Set<Document> documents = getUnsavedDocuments();
    boolean psiBasedIndex = myPsiDependentIndices.contains(indexId);
    if(psiBasedIndex) {
      Set<Document> transactedDocuments = getTransactedDocuments();
      if (documents.size() == 0) documents = transactedDocuments;
      else if (transactedDocuments.size() > 0) {
        documents = new THashSet<Document>(documents);
        documents.addAll(transactedDocuments);
      }
    }

    if (!documents.isEmpty()) {
      Collection<Document> documentsToProcessForProject = ContainerUtil.filter(documents, new Condition<Document>() {
        @Override
        public boolean value(Document document) {
          return belongsToScope(myFileDocumentManager.getFile(document), restrictedFile, filter);
        }
      });

      if (!documentsToProcessForProject.isEmpty()) {
        final StorageGuard.StorageModeExitHandler guard = setDataBufferingEnabled(true);
        try {
          DocumentUpdateTask task = myUnsavedDataUpdateTasks.get(indexId);
          assert task != null : "Task for unsaved data indexing was not initialized for index " + indexId;

          boolean processedAll = task.processAll(documentsToProcessForProject, project) && documentsToProcessForProject.size() == documents.size();

          if (processedAll && !hasActiveTransactions()) {
            ProgressManager.checkCanceled();
            myUpToDateIndicesForUnsavedOrTransactedDocuments.add(indexId);
          }
        }
        finally {
          guard.leave();
        }
      }
    }
  }

  private boolean hasActiveTransactions() {
    return !myTransactionMap.isEmpty();
  }

  private interface DocumentContent {
    CharSequence getText();

    long getModificationStamp();
  }

  private static class AuthenticContent implements DocumentContent {
    private final Document myDocument;

    private AuthenticContent(final Document document) {
      myDocument = document;
    }

    @Override
    public CharSequence getText() {
      return myDocument.getImmutableCharSequence();
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
    public CharSequence getText() {
      if (myFile.getViewProvider().getModificationStamp() != myDocument.getModificationStamp()) {
        final ASTNode node = myFile.getNode();
        assert node != null;
        return node.getChars();
      }
      return myDocument.getImmutableCharSequence();
    }

    @Override
    public long getModificationStamp() {
      return myFile.getViewProvider().getModificationStamp();
    }
  }

  private static final Key<WeakReference<FileContentImpl>> ourFileContentKey = Key.create("unsaved.document.index.content");

  // returns false if doc was not indexed because it is already up to date
  // return true if document was indexed
  // caller is responsible to ensure no concurrent same document processing
  private boolean indexUnsavedDocument(@NotNull final Document document, @NotNull final ID<?, ?> requestedIndexId, final Project project,
                                       @NotNull final VirtualFile vFile) {
    final PsiFile dominantContentFile = project == null ? null : findLatestKnownPsiForUncomittedDocument(document, project);

    final DocumentContent content;
    if (dominantContentFile != null && dominantContentFile.getViewProvider().getModificationStamp() != document.getModificationStamp()) {
      content = new PsiContent(document, dominantContentFile);
    }
    else {
      content = new AuthenticContent(document);
    }

    boolean psiBasedIndex = myPsiDependentIndices.contains(requestedIndexId);

    final long currentDocStamp = psiBasedIndex ? PsiDocumentManager.getInstance(project).getLastCommittedStamp(document) : content.getModificationStamp();

    final long previousDocStamp = myLastIndexedDocStamps.get(document, requestedIndexId);
    if (previousDocStamp == currentDocStamp) return false;

    final CharSequence contentText = content.getText();
    myFileTypeManager.freezeFileTypeTemporarilyIn(vFile, new Runnable() {
      @Override
      public void run() {
        if (!isTooLarge(vFile, contentText.length()) &&
            getAffectedIndexCandidates(vFile).contains(requestedIndexId) &&
            getInputFilter(requestedIndexId).acceptInput(vFile)) {
          // Reasonably attempt to use same file content when calculating indices as we can evaluate them several at once and store in file content
          WeakReference<FileContentImpl> previousContentRef = document.getUserData(ourFileContentKey);
          FileContentImpl previousContent = com.intellij.reference.SoftReference.dereference(previousContentRef);
          final FileContentImpl newFc;
          if (previousContent != null && previousContent.getStamp() == currentDocStamp) {
            newFc = previousContent;
          }
          else {
            newFc = new FileContentImpl(vFile, contentText, vFile.getCharset(), currentDocStamp);
            document.putUserData(ourFileContentKey, new WeakReference<FileContentImpl>(newFc));
          }

          initFileContent(newFc, project, dominantContentFile);

          if (content instanceof AuthenticContent) {
            newFc.putUserData(PlatformIdTableBuilding.EDITOR_HIGHLIGHTER,
                              EditorHighlighterCache.getEditorHighlighterForCachesBuilding(document));
          }

          final int inputId = Math.abs(getFileId(vFile));
          try {
            getIndex(requestedIndexId).update(inputId, newFc).compute();
          }
          finally {
            cleanFileContent(newFc, dominantContentFile);
          }
        }

        long previousState = myLastIndexedDocStamps.set(document, requestedIndexId, currentDocStamp);
        assert previousState == previousDocStamp;
      }
    });

    return true;
  }

  private final TaskQueue myContentlessIndicesUpdateQueue = new TaskQueue(10000);

  private final StorageGuard myStorageLock = new StorageGuard();
  private volatile boolean myPreviousDataBufferingState;
  private final Object myBufferingStateUpdateLock = new Object();

  @NotNull
  private StorageGuard.StorageModeExitHandler setDataBufferingEnabled(final boolean enabled) {
    StorageGuard.StorageModeExitHandler storageModeExitHandler = myStorageLock.enter(enabled);

    if (myPreviousDataBufferingState != enabled) {
      synchronized (myBufferingStateUpdateLock) {
        if (myPreviousDataBufferingState != enabled) {
          for (ID<?, ?> indexId : myIndices.keySet()) {
            final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
            assert index != null;
            ((MemoryIndexStorage)index.getStorage()).setBufferingEnabled(enabled);
          }
          myPreviousDataBufferingState = enabled;
        }
      }
    }
    return storageModeExitHandler;
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
      FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  @Override
  public void requestRebuild(final ID<?, ?> indexId, final Throwable throwable) {
    cleanupProcessedFlag();
    final AtomicInteger status = ourRebuildStatus.get(indexId);
    boolean requiresRebuildWasSet = status.compareAndSet(OK, REQUIRES_REBUILD);

    if (requiresRebuildWasSet) {
      String message = "Rebuild requested for index " + indexId;
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() && app.isReadAccessAllowed() && !app.isDispatchThread()) {
        // shouldn't happen in tests in general; so fail early with the exception that caused index to be rebuilt.
        // otherwise reindexing will fail anyway later, but with a much more cryptic assertion
        LOG.error(message, throwable);
      } else {
        LOG.info(message, throwable);
      }

      cleanupProcessedFlag();

      advanceIndexVersion(indexId);

      final Runnable rebuildRunnable = new Runnable() {
        @Override
        public void run() {
          final Ref<Boolean> success = Ref.create(true);
          new Task.Modal(null, "Updating index", false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
              indicator.setIndeterminate(true);
              try {
                doClearIndex(indexId);
              }
              catch (StorageException e) {
                success.set(false);
                requestRebuild(indexId);
                LOG.info(e);
              }
            }
          }.queue();
          if (success.get()) {
            scheduleIndexRebuild("checkRebuild");
            status.compareAndSet(REQUIRES_REBUILD, OK);
          }
        }
      };

      if (myIsUnitTestMode) {
        rebuildRunnable.run();
      }
      else {
        // we do invoke later since we can have read lock acquired
        //noinspection SSBasedInspection
        ApplicationManager.getApplication().invokeLater(rebuildRunnable, ModalityState.NON_MODAL);
      }
    }
  }

  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    //noinspection unchecked
    return (UpdatableIndex<K, V, FileContent>)pair.getFirst();
  }

  private InputFilter getInputFilter(@NotNull ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    return pair.getSecond();
  }

  public int getChangedFileCount() {
    return myChangedFilesCollector.getAllFilesToUpdate().size();
  }

  @NotNull
  public Collection<VirtualFile> getFilesToUpdate(final Project project) {
    return ContainerUtil.findAll(myChangedFilesCollector.getAllFilesToUpdate(), new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile virtualFile) {
        if (virtualFile instanceof DeletedVirtualFileStub) {
          return true;
        }
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

  public boolean isFileUpToDate(VirtualFile file) {
    return !myChangedFilesCollector.myFilesToUpdate.containsKey(Math.abs(getIdMaskingNonIdBasedFile(file)));
  }

  // caller is responsible to ensure no concurrent same document processing
  void processRefreshedFile(@NotNull Project project, @NotNull final com.intellij.ide.caches.FileContent fileContent) {
    // ProcessCanceledException will cause re-adding the file to processing list
    final VirtualFile file = fileContent.getVirtualFile();
    if (myChangedFilesCollector.myFilesToUpdate.containsKey(Math.abs(getIdMaskingNonIdBasedFile(file)))) {
      indexFileContent(project, fileContent);
    }
  }

  public void indexFileContent(@Nullable Project project, @NotNull com.intellij.ide.caches.FileContent content) {
    VirtualFile file = content.getVirtualFile();
    final int fileId = Math.abs(getIdMaskingNonIdBasedFile(file));

    try {
      // if file was scheduled for update due to vfs events then it is present in myFilesToUpdate
      // in this case we consider that current indexing (out of roots backed CacheUpdater) will cover its content
      // todo this assumption isn't correct for vfs events happened between content loading and indexing itself
      // proper fix will when events handling will be out of direct execution by EDT
      if (!file.isValid() || isTooLarge(file)) {
        removeDataFromIndicesForFile(fileId);
        if (file instanceof DeletedVirtualFileStub && ((DeletedVirtualFileStub)file).isResurrected()) {
          doIndexFileContent(project, new com.intellij.ide.caches.FileContent(((DeletedVirtualFileStub)file).getOriginalFile()));
        }
      } else {
        doIndexFileContent(project, content);
      }
    }
    finally {
      IndexingStamp.flushCache(fileId);
    }

    myChangedFilesCollector.myFilesToUpdate.remove(fileId);
  }

  private void doIndexFileContent(@Nullable Project project, @NotNull final com.intellij.ide.caches.FileContent content) {
    final VirtualFile file = content.getVirtualFile();

    final FileType fileType = file.getFileType();
    final Project finalProject = project == null ? ProjectUtil.guessProjectForFile(file) : project;
    myFileTypeManager.freezeFileTypeTemporarilyIn(file, new Runnable() {
      @Override
      public void run() {
        PsiFile psiFile = null;
        FileContentImpl fc = null;
        int inputId = -1;

        final List<ID<?, ?>> affectedIndexCandidates = getAffectedIndexCandidates(file);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = affectedIndexCandidates.size(); i < size; ++i) {
          final ID<?, ?> indexId = affectedIndexCandidates.get(i);
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

              if (!fileType.isBinary() && IdIndex.ourSnapshotMappingsEnabled) {
                try {
                  byte[] hash = ContentHashesSupport.calcContentHashWithFileType(
                    currentBytes,
                    fc.getCharset(),
                    SubstitutedFileType.substituteFileType(file, fileType, finalProject)
                  );
                  fc.setHash(hash);
                } catch (IOException e) {
                  LOG.error(e);
                }
              }

              psiFile = content.getUserData(IndexingDataKeys.PSI_FILE);
              initFileContent(fc, finalProject, psiFile);
              inputId = Math.abs(getFileId(file));
            }

            try {
              ProgressManager.checkCanceled();
              updateSingleIndex(indexId, inputId, fc);
            }
            catch (ProcessCanceledException e) {
              cleanFileContent(fc, psiFile);
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
    });
  }

  public boolean isIndexingCandidate(@NotNull VirtualFile file, @NotNull ID<?, ?> indexId) {
    return !isTooLarge(file) && getAffectedIndexCandidates(file).contains(indexId);
  }

  @NotNull
  private List<ID<?, ?>> getAffectedIndexCandidates(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      return isProjectOrWorkspaceFile(file, null) ?  Collections.<ID<?,?>>emptyList() : myIndicesForDirectories;
    }
    FileType fileType = file.getFileType();
    if(isProjectOrWorkspaceFile(file, fileType)) return Collections.emptyList();
    List<ID<?, ?>> ids = myFileType2IndicesWithFileTypeInfoMap.get(fileType);
    if (ids == null) ids = myIndicesWithoutFileTypeInfo;
    return ids;
  }

  private static void cleanFileContent(@NotNull FileContentImpl fc, PsiFile psiFile) {
    if (psiFile != null) psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
    fc.putUserData(IndexingDataKeys.PSI_FILE, null);
  }

  private static void initFileContent(@NotNull FileContentImpl fc, Project project, PsiFile psiFile) {
    if (psiFile != null) {
      psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
      fc.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
    }

    fc.putUserData(IndexingDataKeys.PROJECT, project);
  }

  static final Key<Boolean> ourPhysicalContentKey = Key.create("physical.content.flag");

  void updateSingleIndex(@NotNull ID<?, ?> indexId, final int inputId, @Nullable FileContent currentFC)
    throws StorageException {
    if (ourRebuildStatus.get(indexId).get() == REQUIRES_REBUILD && !myIsUnitTestMode) {
      return; // the index is scheduled for rebuild, no need to update
    }
    myLocalModCount++;

    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null;

    boolean hasContent = currentFC != null;
    if (hasContent && currentFC.getUserData(ourPhysicalContentKey) == null) {
      currentFC.putUserData(ourPhysicalContentKey, Boolean.TRUE);
    }

    boolean updateCalculated = false;
    try {
      // important: no hard referencing currentFC to avoid OOME, the methods introduced for this purpose!
      // important: update is called out of try since possible indexer extension is HANDLED as single file fail / restart indexing policy
      final Computable<Boolean> update = index.update(inputId, currentFC);
      updateCalculated = true;

      scheduleUpdate(indexId, update, inputId, hasContent);
    } catch (RuntimeException exception) {
      Throwable causeToRebuildIndex = getCauseToRebuildIndex(exception);
      if (causeToRebuildIndex != null && (updateCalculated || causeToRebuildIndex instanceof IOException)) {
        requestRebuild(indexId, exception);
        return;
      }
      throw exception;
    }
  }

  private class VirtualFileUpdateTask extends UpdateTask<VirtualFile> {
    @Override
    void doProcess(VirtualFile item, Project project) {
      processRefreshedFile(project, new com.intellij.ide.caches.FileContent(item));
    }
  }

  private final VirtualFileUpdateTask myForceUpdateTask = new VirtualFileUpdateTask();
  private final AtomicInteger myForceUpdateRequests = new AtomicInteger();

  private void forceUpdate(@Nullable Project project, @Nullable final GlobalSearchScope filter, @Nullable final VirtualFile restrictedTo) {
    Collection<VirtualFile> allFilesToUpdate = myChangedFilesCollector.getAllFilesToUpdate();

    if (!allFilesToUpdate.isEmpty()) {
      boolean includeFilesFromOtherProjects = restrictedTo == null && (myForceUpdateRequests.incrementAndGet() & 0x3F) == 0;
      List<VirtualFile> virtualFilesToBeUpdatedForProject = ContainerUtil.filter(
        allFilesToUpdate,
        new ProjectFilesCondition(projectIndexableFiles(project), filter, restrictedTo,
                                  includeFilesFromOtherProjects)
      );

      if (!virtualFilesToBeUpdatedForProject.isEmpty()) {
        myForceUpdateTask.processAll(virtualFilesToBeUpdatedForProject, project);
      }
    }
  }

  private void scheduleUpdate(@NotNull final ID<?, ?> indexId, final Computable<Boolean> update, final int inputId, final boolean hasContent) {
    if (myNotRequiringContentIndices.contains(indexId) && !Registry.is("idea.concurrent.scanning.files.to.index")) {
      myContentlessIndicesUpdateQueue.submit(
        new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return updateWithBufferingEnabled(update);
          }
        },
        new Runnable() {
          @Override
          public void run() {
            indexedStampUpdate(indexId, inputId, hasContent);
          }
        });
    }
    else {
      if (updateWithBufferingEnabled(update)) {
        AccessToken accessToken = ReadAction.start();
        try {
          indexedStampUpdate(indexId, inputId, hasContent);
        } finally {
          accessToken.finish();
        }
      }
    }
  }

  protected void indexedStampUpdate(@NotNull ID<?, ?> indexId, int fileId, boolean hasContent) {
    if (hasContent) {
      IndexingStamp.setFileIndexedStateCurrent(fileId, indexId);
    }
    else {
      IndexingStamp.setFileIndexedStateOutdated(fileId, indexId);
    }
    if (myNotRequiringContentIndices.contains(indexId)) IndexingStamp.flushCache(fileId);
  }

  protected boolean updateWithBufferingEnabled(@NotNull final Computable<Boolean> update) {
    final StorageGuard.StorageModeExitHandler lock = setDataBufferingEnabled(false);
    try {
      return update.compute();
    }
    finally {
      lock.leave();
    }
  }

  private boolean needsFileContentLoading(@NotNull ID<?, ?> indexId) {
    return !myNotRequiringContentIndices.contains(indexId);
  }

  private @Nullable IndexableFileSet getIndexableSetForFile(VirtualFile file) {
    for (IndexableFileSet set : myIndexableSets) {
      if (set.isInSet(file)) {
        return set;
      }
    }
    return null;
  }

  private void doInvalidateIndicesForFile(@NotNull final VirtualFile file, boolean contentChanged) {
    cleanProcessedFlag(file);

    final int fileId = Math.abs(getIdMaskingNonIdBasedFile(file));
    IndexingStamp.flushCache(fileId);
    List<ID<?, ?>> nontrivialFileIndexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);

    if (!contentChanged) {
      for (ID<?, ?> indexId : nontrivialFileIndexedStates) {
        if (myNotRequiringContentIndices.contains(indexId)) {
          try {
            updateSingleIndex(indexId, fileId, null);
          }
          catch (StorageException e) {
            LOG.info(e);
            requestRebuild(indexId);
          }
        }
      }
      myChangedFilesCollector.removeScheduledFileFromUpdate(file); // no need to update it anymore
    }

    Collection<ID<?, ?>> fileIndexedStatesToUpdate = ContainerUtil.intersection(nontrivialFileIndexedStates, myRequiringContentIndices);

    if (contentChanged) {
      // only mark the file as outdated, reindex will be done lazily
      if (!fileIndexedStatesToUpdate.isEmpty()) {

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = nontrivialFileIndexedStates.size(); i < size; ++i) {
          final ID<?, ?> indexId = nontrivialFileIndexedStates.get(i);
          if (needsFileContentLoading(indexId) && IndexingStamp.isFileIndexedStateCurrent(fileId, indexId)) {
            IndexingStamp.setFileIndexedStateOutdated(fileId, indexId);
          }
        }

        clearUpToDateStateForPsiIndicesOfUnsavedDocuments(file);

        // the file is for sure not a dir and it was previously indexed by at least one index AND it belongs to some update set
        if (!isTooLarge(file) && getIndexableSetForFile(file) != null) myChangedFilesCollector.scheduleForUpdate(file);
      }
    }
    else if (!fileIndexedStatesToUpdate.isEmpty()) { // file was removed, its data should be (lazily) wiped for every index
      myChangedFilesCollector.scheduleForUpdate(new DeletedVirtualFileStub((VirtualFileWithId)file));
    }

    IndexingStamp.flushCache(fileId);
  }

  private void scheduleFileForIndexing(final VirtualFile file, boolean contentChange) {
    // handle 'content-less' indices separately
    boolean fileIsDirectory = file.isDirectory();
    if (!contentChange) {
      FileContent fileContent = null;
      int inputId = -1;
      for (ID<?, ?> indexId : fileIsDirectory ? myIndicesForDirectories : myNotRequiringContentIndices) {
        if (getInputFilter(indexId).acceptInput(file)) {
          try {
            if (fileContent == null) {
              fileContent = new FileContentImpl(file);
              inputId = Math.abs(getFileId(file));
            }
            updateSingleIndex(indexId, inputId, fileContent);
          }
          catch (StorageException e) {
            LOG.info(e);
            requestRebuild(indexId);
          }
        }
      }
    }
    // For 'normal indices' schedule the file for update and reset stamps for all affected indices (there
    // can be client that used indices between before and after events, in such case indices are up to date due to force update
    // with old content)
    if (!fileIsDirectory) {
      if (isTooLarge(file)) {
        // large file might be scheduled for update in before event when its size was not large
        myChangedFilesCollector.removeScheduledFileFromUpdate(file);
      }
      else {
        myFileTypeManager.freezeFileTypeTemporarilyIn(file, new Runnable() {
          @Override
          public void run() {
            final List<ID<?, ?>> candidates = getAffectedIndexCandidates(file);
            int fileId = getIdMaskingNonIdBasedFile(file);
            //noinspection ForLoopReplaceableByForEach
            boolean scheduleForUpdate = false;
            boolean resetStamp = false;

            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = candidates.size(); i < size; ++i) {
              final ID<?, ?> indexId = candidates.get(i);
              if (needsFileContentLoading(indexId) && getInputFilter(indexId).acceptInput(file)) {
                if (IndexingStamp.isFileIndexedStateCurrent(fileId, indexId)) {
                  IndexingStamp.setFileIndexedStateOutdated(fileId, indexId);
                  resetStamp = true;
                }
                scheduleForUpdate = true;
              }
            }

          if (scheduleForUpdate) {
            if (resetStamp) IndexingStamp.flushCache(fileId);
            myChangedFilesCollector.scheduleForUpdate(file);
          }

            if (!myUpToDateIndicesForUnsavedOrTransactedDocuments.isEmpty()) {
              clearUpToDateStateForPsiIndicesOfUnsavedDocuments(file);
            }
          }
        });
      }
    }
  }

  private final class ChangedFilesCollector extends IndexedFilesListener {
    private final ConcurrentIntObjectMap<VirtualFile> myFilesToUpdate = ContainerUtil.createConcurrentIntObjectMap();

    private final ManagingFS myManagingFS = ManagingFS.getInstance();

    @Override
    protected void buildIndicesForFileRecursively(@NotNull VirtualFile file, boolean contentChange) {
      cleanProcessedFlag(file);
      if (!contentChange) {
        myUpdatingFiles.incrementAndGet();
      }

      super.buildIndicesForFileRecursively(file, contentChange);

      IndexingStamp.flushCaches();
      if (!contentChange) {
        if (myUpdatingFiles.decrementAndGet() == 0) {
          ++myFilesModCount;
        }
      }
    }

    @Override
    protected void iterateIndexableFiles(VirtualFile file, ContentIterator iterator) {
      for (IndexableFileSet set : myIndexableSets) {
        if (set.isInSet(file)) {
          set.iterateIndexableFilesIn(file, iterator);
        }
      }
    }

    @Override
    protected void buildIndicesForFile(VirtualFile file, boolean contentChange) {
      scheduleFileForIndexing(file, contentChange);
    }

    @Override
    protected boolean invalidateIndicesForFile(VirtualFile file, boolean contentChange) {
      if (isUnderConfigOrSystem(file)) {
        return false;
      }
      if (file.isDirectory()) {
        doInvalidateIndicesForFile(file, contentChange);
        if (!isMock(file) && !myManagingFS.wereChildrenAccessed(file)) {
          return false;
        }
      }
      else {
        doInvalidateIndicesForFile(file, contentChange);
      }
      return true;
    }

    void scheduleForUpdate(VirtualFile file) {
      final int fileId = Math.abs(getIdMaskingNonIdBasedFile(file));
      final VirtualFile previousVirtualFile = myFilesToUpdate.put(fileId, file);

      if (previousVirtualFile instanceof DeletedVirtualFileStub &&
          !previousVirtualFile.equals(file)) {
        assert ((DeletedVirtualFileStub)previousVirtualFile).getOriginalFile().equals(file);
        ((DeletedVirtualFileStub)previousVirtualFile).setResurrected(true);
        myFilesToUpdate.put(fileId, previousVirtualFile);
      }
    }

    private void removeScheduledFileFromUpdate(VirtualFile file) {
      final int fileId = Math.abs(getIdMaskingNonIdBasedFile(file));
      final VirtualFile previousVirtualFile = myFilesToUpdate.remove(fileId);

      if (previousVirtualFile instanceof DeletedVirtualFileStub) {
        assert ((DeletedVirtualFileStub)previousVirtualFile).getOriginalFile().equals(file);
        ((DeletedVirtualFileStub)previousVirtualFile).setResurrected(false);
        myFilesToUpdate.put(fileId, previousVirtualFile);
      }
    }

    public Collection<VirtualFile> getAllFilesToUpdate() {
      if (myFilesToUpdate.isEmpty()) {
        return Collections.emptyList();
      }
      return new ArrayList<VirtualFile>(myFilesToUpdate.values());
    }

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
      myContentlessIndicesUpdateQueue.signalUpdateStart();
      myContentlessIndicesUpdateQueue.ensureUpToDate();

      for (VFileEvent event : events) {
        if (memoryStorageCleaningNeeded(event)) {
          cleanupMemoryStorage();
          break;
        }
      }
      super.before(events);
    }

    private boolean memoryStorageCleaningNeeded(VFileEvent event) {
      Object requestor = event.getRequestor();
      return requestor instanceof FileDocumentManager ||
          requestor instanceof PsiManager ||
          requestor == LocalHistory.VFS_EVENT_REQUESTOR;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      myContentlessIndicesUpdateQueue.ensureUpToDate();

      super.after(events);
      myContentlessIndicesUpdateQueue.signalUpdateEnd();
    }
  }

  private boolean clearUpToDateStateForPsiIndicesOfUnsavedDocuments(@NotNull VirtualFile file) {
    Document document = myFileDocumentManager.getCachedDocument(file);

    if (document != null && myFileDocumentManager.isDocumentUnsaved(document)) {
      if (!myUpToDateIndicesForUnsavedOrTransactedDocuments.isEmpty()) {
        for (ID<?, ?> psiBackedIndex : myPsiDependentIndices) {
          myUpToDateIndicesForUnsavedOrTransactedDocuments.remove(psiBackedIndex);
        }
      }

      myLastIndexedDocStamps.clearForDocument(document); // Q: non psi indices
      document.putUserData(ourFileContentKey, null);

      return true;
    }
    return false;
  }

  private static int getIdMaskingNonIdBasedFile(VirtualFile file) {
    return file instanceof VirtualFileWithId ?((VirtualFileWithId)file).getId() : IndexingStamp.INVALID_FILE_ID;
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
      List<VirtualFile> files;
      synchronized (myFiles) {
        files = myFiles;
      }

      // When processing roots concurrently myFiles looses the local order of local vs archive files
      // If we process the roots in 2 threads we can just separate local vs archive
      List<VirtualFile> localFileSystemFiles = new ArrayList<VirtualFile>(files.size() / 2);
      List<VirtualFile> archiveFiles = new ArrayList<VirtualFile>(files.size() / 2);
      for(VirtualFile file:files) {
        if (file.getFileSystem() instanceof LocalFileSystem) localFileSystemFiles.add(file);
        else archiveFiles.add(file);
      }

      localFileSystemFiles.addAll(archiveFiles);
      return localFileSystemFiles;
    }

    @Override
    public boolean processFile(@NotNull final VirtualFile file) {
      if (!file.isValid()) {
        return true;
      }
      if (file instanceof VirtualFileSystemEntry && ((VirtualFileSystemEntry)file).isFileIndexed()) {
        return true;
      }

      if (!(file instanceof VirtualFileWithId)) {
        return true;
      }
      myFileTypeManager.freezeFileTypeTemporarilyIn(file, new Runnable() {
        @Override
        public void run() {
          boolean oldStuff = true;
          if (file.isDirectory() || !isTooLarge(file)) {
            final List<ID<?, ?>> affectedIndexCandidates = getAffectedIndexCandidates(file);
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = affectedIndexCandidates.size(); i < size; ++i) {
              final ID<?, ?> indexId = affectedIndexCandidates.get(i);
              try {
                if (needsFileContentLoading(indexId) && shouldIndexFile(file, indexId)) {
                  synchronized (myFiles) {
                    myFiles.add(file);
                  }
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
          int inputId = Math.abs(getIdMaskingNonIdBasedFile(file));
          for (ID<?, ?> indexId : myNotRequiringContentIndices) {
            if (shouldIndexFile(file, indexId)) {
              oldStuff = false;
              try {
                if (fileContent == null) {
                  fileContent = new FileContentImpl(file);
                }
                updateSingleIndex(indexId, inputId, fileContent);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
          IndexingStamp.flushCache(inputId);

          if (oldStuff && file instanceof VirtualFileSystemEntry) {
            ((VirtualFileSystemEntry)file).setFileIndexed(true);
          }
        }
      });

      if (myProgressIndicator != null && file.isDirectory()) { // once for dir is cheap enough
        myProgressIndicator.checkCanceled();
        myProgressIndicator.setText("Scanning files to index");
      }
      return true;
    }
  }

  private boolean shouldIndexFile(@NotNull VirtualFile file, @NotNull ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || !IndexingStamp.isFileIndexedStateCurrent(((NewVirtualFile)file).getId(), indexId));
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
      return !myNoLimitCheckTypes.contains(file.getFileType()) || SingleRootFileViewProvider.isTooLargeForContentLoading(file);
    }
    return false;
  }

  private boolean isTooLarge(@NotNull VirtualFile file, long contentSize) {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file, contentSize)) {
      return !myNoLimitCheckTypes.contains(file.getFileType()) || SingleRootFileViewProvider.isTooLargeForContentLoading(file, contentSize);
    }
    return false;
  }

  @NotNull
  public CollectingContentIterator createContentIterator(@Nullable ProgressIndicator indicator) {
    return new UnindexedFilesFinder(indicator);
  }

  @Override
  public void registerIndexableSet(@NotNull IndexableFileSet set, @Nullable Project project) {
    myIndexableSets.add(set);
    myIndexableSetToProjectMap.put(set, project);
    if (project != null) {
      ((PsiManagerImpl)PsiManager.getInstance(project)).addTreeChangePreprocessor(new PsiTreeChangePreprocessor() {
        @Override
        public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
          if (event.isGenericChange() &&
              event.getCode() == PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED) {
            PsiFile file = event.getFile();
            if (file != null) {
              VirtualFile virtualFile = file.getVirtualFile();
              if (!clearUpToDateStateForPsiIndicesOfUnsavedDocuments(virtualFile)) {
                // change in persistent file
                if (virtualFile instanceof VirtualFileWithId) {
                  int fileId = ((VirtualFileWithId)virtualFile).getId();
                  boolean wasIndexed = false;
                  for (ID<?, ?> psiBackedIndex : myPsiDependentIndices) {
                    if (IndexingStamp.isFileIndexedStateCurrent(fileId, psiBackedIndex)) {
                      IndexingStamp.setFileIndexedStateOutdated(fileId, psiBackedIndex);
                      wasIndexed = true;
                    }
                  }
                  if (wasIndexed) {
                    myChangedFilesCollector.scheduleForUpdate(virtualFile);
                    IndexingStamp.flushCache(fileId);
                  }
                }
              }
            }
          }
        }
      });
    }
  }

  @Override
  public void removeIndexableSet(@NotNull IndexableFileSet set) {
    if (!myIndexableSetToProjectMap.containsKey(set)) return;
    myIndexableSets.remove(set);
    myIndexableSetToProjectMap.remove(set);

    for (VirtualFile file : myChangedFilesCollector.getAllFilesToUpdate()) {
      final int fileId = Math.abs(getIdMaskingNonIdBasedFile(file));
      if (!file.isValid()) {
        removeDataFromIndicesForFile(fileId);
        myChangedFilesCollector.myFilesToUpdate.remove(fileId);
      } else if (getIndexableSetForFile(file) == null) { // todo remove data from indices for removed
        myChangedFilesCollector.myFilesToUpdate.remove(fileId);
      }
    }

    IndexingStamp.flushCaches();
  }

  @Override
  public VirtualFile findFileById(Project project, int id) {
    return IndexInfrastructure.findFileById((PersistentFS)ManagingFS.getInstance(), id);
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
    public boolean acceptInput(@NotNull final VirtualFile file) {
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
    if (!(file instanceof VirtualFileSystemEntry)) return;

    final VirtualFileSystemEntry nvf = (VirtualFileSystemEntry)file;
    if (file.isDirectory()) {
      nvf.setFileIndexed(false);
      for (VirtualFile child : nvf.getCachedChildren()) {
        cleanProcessedFlag(child);
      }
    }
    else {
      nvf.setFileIndexed(false);
    }
  }

  @Override
  public void iterateIndexableFilesConcurrently(@NotNull ContentIterator processor, @NotNull Project project, ProgressIndicator indicator) {
    PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(collectScanRootRunnables(processor, project, indicator));
  }

  @Override
  public void iterateIndexableFiles(@NotNull final ContentIterator processor, @NotNull final Project project, final ProgressIndicator indicator) {
    for(Runnable r: collectScanRootRunnables(processor, project, indicator)) r.run();
  }

  @NotNull
  private static List<Runnable> collectScanRootRunnables(@NotNull final ContentIterator processor,
                                                         @NotNull final Project project,
                                                         final ProgressIndicator indicator) {
    if (project.isDisposed()) {
      return Collections.emptyList();
    }

    List<Runnable> tasks = new ArrayList<Runnable>();

    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    tasks.add(new Runnable() {
      @Override
      public void run() {
        projectFileIndex.iterateContent(processor);
      }
    });
    /*
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for(final Module module: modules) {
      tasks.add(new Runnable() {
        @Override
        public void run() {
          if (module.isDisposed()) return;
          ModuleRootManager.getInstance(module).getFileIndex().iterateContent(processor);
        }
      });
    }*/

    final Set<VirtualFile> visitedRoots = ContainerUtil.newConcurrentSet();
    for (IndexableSetContributor contributor : Extensions.getExtensions(IndexableSetContributor.EP_NAME)) {
      //important not to depend on project here, to support per-project background reindex
      // each client gives a project to FileBasedIndex
      if (project.isDisposed()) {
        return tasks;
      }
      for (final VirtualFile root : IndexableSetContributor.getRootsToIndex(contributor)) {
        if (visitedRoots.add(root)) {
          tasks.add(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed() || !root.isValid()) return;
              iterateRecursively(root, processor, indicator, visitedRoots, null);
            }
          });
        }
      }
      for (final VirtualFile root : IndexableSetContributor.getProjectRootsToIndex(contributor, project)) {
        if (visitedRoots.add(root)) {
          tasks.add(new Runnable() {
            @Override
            public void run() {
              if (project.isDisposed() || !root.isValid()) return;
              iterateRecursively(root, processor, indicator, visitedRoots, null);
            }
          });
        }
      }
    }

    // iterate associated libraries
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          if (orderEntry.isValid()) {
            final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
            final VirtualFile[] libSources = entry.getRootFiles(OrderRootType.SOURCES);
            final VirtualFile[] libClasses = entry.getRootFiles(OrderRootType.CLASSES);
            for (VirtualFile[] roots : new VirtualFile[][]{libSources, libClasses}) {
              for (final VirtualFile root : roots) {
                if (visitedRoots.add(root)) {
                  tasks.add(new Runnable() {
                    @Override
                    public void run() {
                      if (project.isDisposed() || module.isDisposed() || !root.isValid()) return;
                      iterateRecursively(root, processor, indicator, null, projectFileIndex);
                    }
                  });
                }
              }
            }
          }
        }
      }
    }
    return tasks;
  }

  @SuppressWarnings({"WhileLoopSpinsOnField", "SynchronizeOnThis"})
  private static class StorageGuard {
    private int myHolds;
    private int myWaiters;

    public interface StorageModeExitHandler {
      void leave();
    }

    private final StorageModeExitHandler myTrueStorageModeExitHandler = new StorageModeExitHandler() {
      @Override
      public void leave() {
        StorageGuard.this.leave(true);
      }
    };
    private final StorageModeExitHandler myFalseStorageModeExitHandler = new StorageModeExitHandler() {
      @Override
      public void leave() {
        StorageGuard.this.leave(false);
      }
    };

    @NotNull
    private synchronized StorageModeExitHandler enter(boolean mode) {
      if (mode) {
        while (myHolds < 0) {
          doWait();
        }
        myHolds++;
        return myTrueStorageModeExitHandler;
      }
      else {
        while (myHolds > 0) {
          doWait();
        }
        myHolds--;
        return myFalseStorageModeExitHandler;
      }
    }

    private void doWait() {
      try {
        ++myWaiters;
        wait();
      }
      catch (InterruptedException ignored) {
      } finally {
        --myWaiters;
      }
    }

    private synchronized void leave(boolean mode) {
      myHolds += mode ? -1 : 1;
      if (myHolds == 0 && myWaiters > 0) {
        notifyAll();
      }
    }
  }

  private final class DocumentUpdateTask extends UpdateTask<Document> {
    private final ID<?, ?> myIndexId;

    public DocumentUpdateTask(ID<?, ?> indexId) {
      myIndexId = indexId;
    }

    @Override
    void doProcess(Document document, Project project) {
      indexUnsavedDocument(document, myIndexId, project, myFileDocumentManager.getFile(document));
    }
  }
}
