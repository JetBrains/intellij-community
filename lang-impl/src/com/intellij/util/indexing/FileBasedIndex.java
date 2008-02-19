package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */

@State(
  name = "FileBasedIndex",
  storages = {
  @Storage(
    id = "index",
    file = "$APP_CONFIG$/index.xml")
    }
)
public class FileBasedIndex implements ApplicationComponent, PersistentStateComponent<FileBasedIndexState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndex");
  
  public static final int VERSION = 1;

  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNeedContentLoading = new HashSet<ID<?, ?>>();
  private FileBasedIndexState myPreviouslyRegistered;

  private TObjectLongHashMap<ID<?, ?>> myIndexIdToCreationStamp = new TObjectLongHashMap<ID<?, ?>>();

  private Map<Document, Pair<CharSequence, Long>> myIndexingHistory = Collections.synchronizedMap(new HashMap<Document, Pair<CharSequence, Long>>());
  
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  private ChangedFilesUpdater myChangedFilesUpdater;

  private List<IndexableFileSet> myIndexableSets = new CopyOnWriteArrayList<IndexableFileSet>();
  private Set<ID<?, ?>> myRequiresRebuild = Collections.synchronizedSet(new HashSet<ID<?, ?>>());

  private ExecutorService myInvalidationService = ConcurrencyUtil.newSingleThreadExecutor("FileBasedIndex.InvalidationQueue");
  private final VirtualFileManagerEx myVfManager;

  public static interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }

  public static final class FileContent {
    public final VirtualFile file;
    public final String fileName;
    public final CharSequence content;

    public FileContent(final VirtualFile file, final CharSequence content) {
      this.file = file;
      // remember name explicitly because the file could be renamed afterwards
      fileName = file.getName();
      this.content = content;
    }
  }

  public FileBasedIndex(final VirtualFileManagerEx vfManager) throws IOException {
    myVfManager = vfManager;
    final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
    for (FileBasedIndexExtension<?, ?> extension : extensions) {
      registerIndexer(extension);
    }

    dropUnregisteredIndices();

    myChangedFilesUpdater = new ChangedFilesUpdater();
    vfManager.addVirtualFileListener(myChangedFilesUpdater);
    vfManager.registerRefreshUpdater(myChangedFilesUpdater);
  }

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
  }

  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   * @param extension
   */
  private <K, V> void registerIndexer(final FileBasedIndexExtension<K, V> extension) throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    if (extension.dependsOnFileContent()) {
      myNeedContentLoading.add(name);
    }
    myIndexIdToVersionMap.put(name, version);
    final File versionFile = getVersionFile(name);
    if (readVersion(versionFile) != version) {
      FileUtil.delete(getIndexRootDir(name));
      rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer());
        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final MapReduceIndex<?, ?, FileContent> index = new MapReduceIndex<K, V, FileContent>(extension.getIndexer(), memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, extension.getInputFilter()));
        break;
      }
      catch (IOException e) {
        FileUtil.delete(getIndexRootDir(name));
        rewriteVersion(versionFile, version);
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileBasedIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myChangedFilesUpdater.forceUpdate();
    myInvalidationService.shutdown();
    try {
      myInvalidationService.awaitTermination(30, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }

    for (ID<?, ?> indexId : myIndices.keySet()) {
      getIndex(indexId).dispose();
    }
    
    myVfManager.removeVirtualFileListener(myChangedFilesUpdater);
    myVfManager.unregisterRefreshUpdater(myChangedFilesUpdater);
  }
                
  public FileBasedIndexState getState() {
    return new FileBasedIndexState(myIndices.keySet());
  }

  public void loadState(final FileBasedIndexState state) {
    myPreviouslyRegistered = state;
  }

  @NotNull
  public <K> Collection<K> getAllKeys(final ID<K, ?> indexId) {
    try {
      checkRebuild(indexId);
      indexUnsavedDocuments();
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      return index != null? index.getAllKeys() : Collections.<K>emptyList();
    }
    catch (StorageException e) {
      requestRebuild(indexId);
      LOG.error(e);
    }
    return Collections.emptyList();
  }
  
  @NotNull
  public <K, V> List<V> getValues(final ID<K, V> indexId, K dataKey, Project project) {
    final List<V> values = new ArrayList<V>();
    processValuesImpl(indexId, dataKey, project, true, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        values.add(value);
      }
    });
    return values;
  }

  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(final ID<K, V> indexId, K dataKey, @NotNull Project project) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    processValuesImpl(indexId, dataKey, project, false, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        files.add(file);
      }
    });
    return files;
  }

  
  public static interface ValueProcessor<V> {
    void process(VirtualFile file, V value);
  }
  public <K, V> void processValues(final ID<K, V> indexId, final K dataKey, final Project project, final @Nullable VirtualFile inFile, ValueProcessor<V> processor) {
    processValuesImpl(indexId, dataKey, project, false, inFile, processor);
  }
  
  private <K, V> void processValuesImpl(final ID<K, V> indexId, final K dataKey, final Project project, boolean ensureValueProcessedOnce,
                                        final @Nullable VirtualFile restrictToFile, ValueProcessor<V> processor) {
    try {
      checkRebuild(indexId);
      indexUnsavedDocuments();
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }

      final Lock readLock = index.getReadLock();
      readLock.lock();
      try {
        final ValueContainer<V> container = index.getData(dataKey);

        if (restrictToFile != null) {
          final int restrictedFileId = getFileId(restrictToFile);
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            if (container.isAssociated(value, restrictedFileId)) {
              processor.process(restrictToFile, value);
            }
          }
        }
        else {
          final DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
          final PersistentFS fs = (PersistentFS)PersistentFS.getInstance();
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext();) {
              final int id = inputIdsIterator.next();
              VirtualFile file = findFileById(dirIndex, fs, id);
              if (file != null) {
                processor.process(file, value);
                if (ensureValueProcessedOnce) {
                  break; // continue with the next value
                }
              }
            }
          }
        }
      }
      finally {
        readLock.unlock();
      }
    }
    catch (StorageException e) {
      requestRebuild(indexId);
      LOG.error(e);
    }
  }
  
  private void checkRebuild(final ID<?, ?> indexId) throws StorageException {
    final boolean reallyRemoved = myRequiresRebuild.remove(indexId);
    if (!reallyRemoved) {
      return;
    }
    getIndex(indexId).clear();
    try {
      rewriteVersion(getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }

    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    final FileSystemSynchronizer synchronizer = new FileSystemSynchronizer();
    synchronizer.setCancelable(false);
    for (Project project : projects) {
      synchronizer.registerCacheUpdater(new UnindexedFilesUpdater(project, ProjectRootManager.getInstance(project), this));
      }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      new Task.Modal(null, "Updating index", false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          synchronizer.execute();
        }
      }.queue();
    }
    else {
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      new Task.Backgroundable(null, "Updating index", false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          try {
            synchronizer.execute();
          }
          finally {
            semaphore.up();
          }
        }
      }.queue();
      semaphore.waitFor();
    }
  }

  @Nullable
  private static VirtualFile findFileById(final DirectoryIndex dirIndex, final PersistentFS fs, final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }
    
    final boolean isDirectory = fs.isDirectory(id);
    final DirectoryInfo directoryInfo = isDirectory ? dirIndex.getInfoForDirectoryId(id) : dirIndex.getInfoForDirectoryId(fs.getParent(id));
    if (directoryInfo != null && (directoryInfo.contentRoot != null || directoryInfo.sourceRoot != null)) {
      return isDirectory? directoryInfo.directory : directoryInfo.directory.findChild(fs.getName(id));
    }
    return null;
  }

  @Nullable
  private static VirtualFile findTestFile(final int id) {
    return ourUnitTestMode ? DummyFileSystem.getInstance().findById(id) : null;
  }

  private void indexUnsavedDocuments() throws StorageException {
    myChangedFilesUpdater.forceUpdate();
    
    final FileDocumentManager fdManager = FileDocumentManager.getInstance();
    final Document[] documents = fdManager.getUnsavedDocuments();
    if (documents.length > 0) {
      // now index unsaved data
      setDataBufferingEnabled(true);
      for (Document document : documents) {
        final VirtualFile vFile = fdManager.getFile(document);
        if (!vFile.isValid()) {
          continue; // since the corresponding file is invalid, the document should be ignored
        }
        final Pair<CharSequence, Long> indexingInfo = myIndexingHistory.get(document);
        final long documentStamp = document.getModificationStamp();
        if (indexingInfo == null || documentStamp != indexingInfo.getSecond().longValue()) {
          final FileContent oldFc = new FileContent(
            vFile,
            indexingInfo != null? indexingInfo.getFirst() : loadContent(vFile)
          );
          final FileContent newFc = new FileContent(vFile, document.getText());
          for (ID<?, ?> indexId : myIndices.keySet()) {
            if (getInputFilter(indexId).acceptInput(vFile)) {
              final int inputId = Math.abs(getFileId(vFile));
              getIndex(indexId).update(inputId, newFc, oldFc);
            }
          }
          myIndexingHistory.put(document, new Pair<CharSequence, Long>(newFc.content, documentStamp));
        }
      }
    }
  }

  private void setDataBufferingEnabled(final boolean enabled) {
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final IndexStorage indexStorage = ((MapReduceIndex)getIndex(indexId)).getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }
    for (String s : indicesToDrop) {
      FileUtil.delete(getIndexRootDir(new ID(s)));
    }
  }

  public void requestRebuild(ID<?, ?> indexId) {
    myRequiresRebuild.add(indexId);
  }
  
  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  private InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }

  private long getIndexCreationStamp(ID<?, ?> indexName) {
    long stamp = myIndexIdToCreationStamp.get(indexName);
    if (stamp <= 0) {
      stamp = getVersionFile(indexName).lastModified();
      myIndexIdToCreationStamp.put(indexName, stamp);
    }
    return stamp;
  }
  
  public static File getVersionFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName + ".ver");
  }

  public static File getStorageFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  public static File getIndexRootDir(final ID<?, ?> indexName) {
    final File indexDir = new File(getPersistenceRoot(), indexName.toString().toLowerCase(Locale.US));
    indexDir.mkdirs();
    return indexDir;
  }

  public static File getPersistenceRoot() {
    File file = new File(PathManager.getSystemPath(), "index");
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
  }

  private static int readVersion(final File file) {
    try {
      final DataInputStream in = new DataInputStream(new FileInputStream(file));
      try {
        return in.readInt();
      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      return -1;
    }
  }

  private void rewriteVersion(final File file, final int version) throws IOException {
    FileUtil.delete(file);
    try {
      // need this to ensure the timestamp of the newly created file will be different 
      Thread.sleep(501);
    }
    catch (InterruptedException ignored) {
    }
    file.getParentFile().mkdirs();
    file.createNewFile();
    final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
    try {
      os.writeInt(version);
    }
    finally {
      myIndexIdToCreationStamp.clear();
      os.close();
    }
  }

  public void indexFileContent(com.intellij.ide.startup.FileContent content) {
    final VirtualFile file = content.getVirtualFile();
    FileContent fc = null;
    for (ID<?, ?> indexId : myIndices.keySet()) {
      if (getInputFilter(indexId).acceptInput(file) && !IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId))) {
        if (fc == null) {
          fc = new FileContent(file, CacheUtil.getContentText(content));
        }
        try {
          updateSingleIndex(indexId, file, fc, null);
        }
        catch (StorageException e) {
          requestRebuild(indexId);
          LOG.error(e);
        }
      }
    }
  }

  private void updateSingleIndex(final ID<?, ?> indexId, final VirtualFile file, final FileContent currentFC, final FileContent oldFC)
    throws StorageException {

    setDataBufferingEnabled(false);
    myIndexingHistory.clear();

    final int inputId = Math.abs(getFileId(file));
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    index.update(inputId, currentFC, oldFC);
    if (file.isValid()) {
      if (currentFC != null) {
        IndexingStamp.update(file, indexId, getIndexCreationStamp(indexId));
      }
      else {
        // mark the file as unindexed
        IndexingStamp.update(file, indexId, -1L);
      }
    }
  }

  public static int getFileId(final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    return 0;
  }

  private static CharSequence loadContent(VirtualFile file) {
    return LoadTextUtil.loadText(file, true);
  }

  private final class ChangedFilesUpdater extends VirtualFileAdapter implements CacheUpdater{
    private final Set<VirtualFile> myFileToUpdate = Collections.synchronizedSet(new HashSet<VirtualFile>());
    private BlockingQueue<FutureTask<?>> myFutureInvalidations = new LinkedBlockingQueue<FutureTask<?>>();
    // No need to react on movement events since files stay valid, their ids don't change and all associated attributes remain intact.

    public void fileCreated(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void fileDeleted(final VirtualFileEvent event) {
      myFileToUpdate.remove(event.getFile()); // no need to update it anymore
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      markDirty(event);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile());
    }

    public void beforeContentsChange(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile());
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        final VirtualFile file = event.getFile();
        if (!file.isDirectory()) {
          scheduleInvalidation(file);
        }
      }
    }

    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        if (!event.getFile().isDirectory()) {
          markDirty(event);
        }
      }
    }

    private void markDirty(final VirtualFileEvent event) {
      iterateIndexableFiles(event.getFile(), new Processor<VirtualFile>() {
        public boolean process(final VirtualFile file) {
          for (ID<?, ?> indexId : myIndices.keySet()) {
            if (getInputFilter(indexId).acceptInput(file)) {
              if (myNeedContentLoading.contains(indexId)) {
                myFileToUpdate.add(file);
              }
              else {
                try {
                  updateSingleIndex(indexId, file, new FileContent(file, null), null);
                }
                catch (StorageException e) {
                  LOG.error(e);
                  requestRebuild(indexId);
                }
              }
            }
          }
          return true;
        }
      });
    }

    private void scheduleInvalidation(final VirtualFile file) {
      if (file.isDirectory()) {
        if (!(file instanceof NewVirtualFile) || ManagingFS.getInstance().areChildrenLoaded(file)) {
          for (VirtualFile child : file.getChildren()) {
            scheduleInvalidation(child);
          }
        }
      }
      else {
        final List<ID<?, ?>> affectedIndices = new ArrayList<ID<?, ?>>(myIndices.size());
        for (ID<?, ?> indexId : myIndices.keySet()) {
          if (getInputFilter(indexId).acceptInput(file) && IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId))) {
            if (myNeedContentLoading.contains(indexId)) {
              affectedIndices.add(indexId);
            }
            else {
              // invalidate it synchronously
              try {
                updateSingleIndex(indexId, file, null, new FileContent(file, null));
              }
              catch (StorageException e) {
                LOG.error(e);
                requestRebuild(indexId);
              }
            }
          }
        }
        
        if (affectedIndices.size() > 0) {
          iterateIndexableFiles(file, new Processor<VirtualFile>() {
            public boolean process(final VirtualFile file) {
              myFileToUpdate.add(file);
              return true;
            }
          });
          final FileContent fc = new FileContent(file, loadContent(file));
          final FutureTask<?> future = (FutureTask<?>)myInvalidationService.submit(new Runnable() {
            public void run() {
              for (ID<?, ?> indexId : affectedIndices) {
                try {
                  updateSingleIndex(indexId, file, null, fc);
                }
                catch (StorageException e) {
                  LOG.error(e);
                  requestRebuild(indexId);
                }
              }
            }
          });
          myFutureInvalidations.offer(future);
        }
      }
    }

    private void ensureAllInvalidateTasksCompleted() {
      while (true) {
        final FutureTask<?> future = myFutureInvalidations.poll();
        if (future == null) {
          return;
        }
        future.run(); // force the task run if it is has not been run yet
      }
    }

    private void iterateIndexableFiles(final VirtualFile file, final Processor<VirtualFile> processor) {
      if (file.isDirectory()) {
        final ContentIterator iterator = new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              processor.process(fileOrDir);
            }
            return true;
          }
        };

        for (IndexableFileSet set : myIndexableSets) {
          set.iterateIndexableFilesIn(file, iterator);
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

    public VirtualFile[] queryNeededFiles() {
      synchronized (myFileToUpdate) {
        return myFileToUpdate.toArray(new VirtualFile[myFileToUpdate.size()]);
      }
    }

    public void processFile(final com.intellij.ide.startup.FileContent fileContent) {
      ensureAllInvalidateTasksCompleted();
      processFileImpl(fileContent);
    }

    public void forceUpdate() {
      ensureAllInvalidateTasksCompleted();
      for (VirtualFile file: queryNeededFiles()) {
        processFileImpl(new com.intellij.ide.startup.FileContent(file));
      }
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
    
    private void processFileImpl(final com.intellij.ide.startup.FileContent fileContent) {
      final VirtualFile file = fileContent.getVirtualFile();
      final boolean reallyRemoved = myFileToUpdate.remove(file);
      if (reallyRemoved && file.isValid()) {
        indexFileContent(fileContent);
      }
    }
  }
  
  private class UnindexedFilesFinder implements CollectingContentIterator {
    private final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    private final Collection<ID<?, ?>> myIndexIds;
    private final Collection<ID<?, ?>> mySkipContentLoading;
    private final ProgressIndicator myProgressIndicator;

    public UnindexedFilesFinder(final Collection<ID<?, ?>> indexIds) {
      myIndexIds = new ArrayList<ID<?, ?>>();
      mySkipContentLoading = new ArrayList<ID<?, ?>>();
      for (ID<?, ?> indexId : indexIds) {
        if (myNeedContentLoading.contains(indexId))  {
          myIndexIds.add(indexId);
        }
        else {
          mySkipContentLoading.add(indexId);
        }
      }
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    }

    public List<VirtualFile> getFiles() {
      return myFiles;
    }

    public boolean processFile(final VirtualFile file) {
      if (!file.isDirectory()) {
        for (ID<?, ?> indexId : myIndexIds) {
          if (shouldIndexFile(file, indexId)) {
            myFiles.add(file);
            break;
          }
        }
        for (ID<?, ?> indexId : mySkipContentLoading) {
          if (shouldIndexFile(file, indexId)) {
            try {
              updateSingleIndex(indexId, file, new FileContent(file, null), null);
            }
            catch (StorageException e) {
              LOG.info(e);
              requestRebuild(indexId);
            }
          }
        }
      }
      else {
        if (myProgressIndicator != null) {
          myProgressIndicator.setText("Scanning files to index");
          myProgressIndicator.setText2(file.getPresentableUrl());
        }
      }
      return true;
    }

    private boolean shouldIndexFile(final VirtualFile file, final ID<?, ?> indexId) {
      return getInputFilter(indexId).acceptInput(file) && !IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId));
    }
  }

  public CollectingContentIterator createContentIterator() {
    return new UnindexedFilesFinder(myIndices.keySet());
  }

  public void registerIndexableSet(IndexableFileSet set) {
    myIndexableSets.add(set);
  }

  public void removeIndexableSet(IndexableFileSet set) {
    myIndexableSets.remove(set);
  }
}
