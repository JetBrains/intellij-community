package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.search.CachesBasedRefSearcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */

@State(
  name = "FileBasedIndex",
  roamingType = RoamingType.DISABLED,
  storages = {
  @Storage(
    id = "index",
    file = "$APP_CONFIG$/index.xml")
    }
)
public class FileBasedIndex implements ApplicationComponent, PersistentStateComponent<FileBasedIndexState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndex");

  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNeedContentLoading = new HashSet<ID<?, ?>>();
  private FileBasedIndexState myPreviouslyRegistered;

  private final Map<Document, AtomicLong> myLastIndexedDocStamps = new HashMap<Document, AtomicLong>();
  private final Map<Document, CharSequence> myLastIndexedUnsavedContent = new HashMap<Document, CharSequence>();

  private ChangedFilesUpdater myChangedFilesUpdater;

  private final List<IndexableFileSet> myIndexableSets = new CopyOnWriteArrayList<IndexableFileSet>();
  
  public static final int OK = 1;
  public static final int REQUIRES_REBUILD = 2;
  public static final int REBUILD_IN_PROGRESS = 3;
  private final Map<ID<?, ?>, AtomicInteger> myRebuildStatus = new HashMap<ID<?,?>, AtomicInteger>();
  private static final String MARKER_FILE_NAME = "work_in_progress";

  private final ExecutorService myInvalidationService = ConcurrencyUtil.newSingleThreadExecutor("FileBasedIndex.InvalidationQueue");
  private final VirtualFileManagerEx myVfManager;
  private final Semaphore myUnsavedDataIndexingSemaphore = new Semaphore();

  private final FileContentStorage myFileContentAttic;
  
  public static interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }

  public FileBasedIndex(final VirtualFileManagerEx vfManager) throws IOException {
    myVfManager = vfManager;
    
    final File workInProgressFile = new File(IndexInfrastructure.getPersistenceRoot(), MARKER_FILE_NAME);
    if (workInProgressFile.exists()) {
      // previous IDEA session was closed incorrectly, so drop all indices
      FileUtil.delete(IndexInfrastructure.getPersistenceRoot());
    }

    try {
      final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        myRebuildStatus.put(extension.getName(), new AtomicInteger(OK));
      }
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        registerIndexer(extension);
      }

      dropUnregisteredIndices();

      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myIndices.keySet()) {
        if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, OK)) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.error(e);
          }
        }
      }

      myChangedFilesUpdater = new ChangedFilesUpdater();
      vfManager.addVirtualFileListener(myChangedFilesUpdater);
      vfManager.registerRefreshUpdater(myChangedFilesUpdater);
      myFileContentAttic = new FileContentStorage(new File(IndexInfrastructure.getPersistenceRoot(), "updates.tmp"));
    }
    finally {
      workInProgressFile.createNewFile();
    }

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
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    if (IndexInfrastructure.versionDiffers(versionFile, version)) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getCacheSize());
        final IndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(extension, memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, new IndexableFilesFilter(extension.getInputFilter())));
        break;
      }
      catch (IOException e) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
  }

  private <K, V> UpdatableIndex<K, V, FileContent> createIndex(final FileBasedIndexExtension<K, V> extension, final IndexStorage<K, V> storage) {
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      return ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension).createIndexImplementation(this, storage);
    }
    return new MapReduceIndex<K, V, FileContent>(extension.getIndexer(), storage);
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
      LOG.info(e);
    }

    for (ID<?, ?> indexId : myIndices.keySet()) {
      final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
      assert index != null;
      index.dispose();
    }
    
    myVfManager.removeVirtualFileListener(myChangedFilesUpdater);
    myVfManager.unregisterRefreshUpdater(myChangedFilesUpdater);
    
    final File workInProgressFile = new File(IndexInfrastructure.getPersistenceRoot(), MARKER_FILE_NAME);
    FileUtil.delete(workInProgressFile);
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
      ensureUpToDate(indexId);
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      return index != null? index.getAllKeys() : Collections.<K>emptyList();
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
    return Collections.emptyList();
  }

  public <K> void ensureUpToDate(final ID<K, ?> indexId) {
    try {
      checkRebuild(indexId);
      indexUnsavedDocuments();
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

  @NotNull
  public <K, V> List<V> getValues(final ID<K, V> indexId, K dataKey, final VirtualFileFilter filter) {
    final List<V> values = new ArrayList<V>();
    processValuesImpl(indexId, dataKey, true, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        values.add(value);
      }
    }, filter);
    return values;
  }

  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(final ID<K, V> indexId, K dataKey, final VirtualFileFilter filter) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    processValuesImpl(indexId, dataKey, false, null, new ValueProcessor<V>() {
      public void process(final VirtualFile file, final V value) {
        files.add(file);
      }
    }, filter);
    return files;
  }

  
  public static interface ValueProcessor<V> {
    void process(VirtualFile file, V value);
  }

  public <K, V> void processValues(final ID<K, V> indexId, final K dataKey, final @Nullable VirtualFile inFile,
                                   ValueProcessor<V> processor, final VirtualFileFilter filter) {
    processValuesImpl(indexId, dataKey, false, inFile, processor, filter);
  }
  
  private <K, V> void processValuesImpl(final ID<K, V> indexId, final K dataKey, boolean ensureValueProcessedOnce,
                                        final @Nullable VirtualFile restrictToFile, ValueProcessor<V> processor,
                                        final VirtualFileFilter filter) {
    try {
      ensureUpToDate(indexId);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }
  
      final Lock readLock = index.getReadLock();
      readLock.lock();
      try {
        final ValueContainer<V> container = index.getData(dataKey);
  
        if (restrictToFile != null) {
          if (!(restrictToFile instanceof VirtualFileWithId)) return;
          
          final int restrictedFileId = getFileId(restrictToFile);
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            if (container.isAssociated(value, restrictedFileId)) {
              processor.process(restrictToFile, value);
            }
          }
        }
        else {
          final PersistentFS fs = (PersistentFS)PersistentFS.getInstance();
          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext();) {
              final int id = inputIdsIterator.next();
              VirtualFile file = IndexInfrastructure.findFileById(fs, id);
              if (file != null && filter.accept(file)) {
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
  
  public static interface AllValuesProcessor<V> {
    void process(final int inputId, V value);
  }
  
  public <K, V> void processAllValues(final ID<K, V> indexId, AllValuesProcessor<V> processor) {
    try {
      ensureUpToDate(indexId);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }
  
      final Lock readLock = index.getReadLock();
      readLock.lock();
      try {
        for (K dataKey : index.getAllKeys()) {
          final ValueContainer<V> container = index.getData(dataKey);
          for (final Iterator<V> it = container.getValueIterator(); it.hasNext();) {
            final V value = it.next();
            for (final ValueContainer.IntIterator inputsIt = container.getInputIdsIterator(value); inputsIt.hasNext();) {
              processor.process(inputsIt.next(), value);
            }
          }
        }
      }
      finally {
        readLock.unlock();
      }
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

  private <K> void scheduleRebuild(final ID<K, ?> indexId, final Throwable e) {
    requestRebuild(indexId);
    LOG.info(e);
    checkRebuild(indexId);
  }

  private void checkRebuild(final ID<?, ?> indexId) {
    if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, REBUILD_IN_PROGRESS)) {

      final FileSystemSynchronizer synchronizer = new FileSystemSynchronizer();
      synchronizer.setCancelable(false);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        synchronizer.registerCacheUpdater(new UnindexedFilesUpdater(project, ProjectRootManager.getInstance(project), this));
      }

      final Runnable rebuildRunnable = new Runnable() {
        public void run() {
          try {
            clearIndex(indexId);
            synchronizer.execute();
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.info(e);
          }
          finally {
            myRebuildStatus.get(indexId).compareAndSet(REBUILD_IN_PROGRESS, OK);
          }
        }
      };
      
      final Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        rebuildRunnable.run();
      }
      else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            new Task.Modal(null, "Updating index", false) {
              public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                rebuildRunnable.run();
              }
            }.queue();
          }
        });
      }    
    }
    
    if (myRebuildStatus.get(indexId).get() == REBUILD_IN_PROGRESS) {
      throw new ProcessCanceledException();
    }
  }

  private void clearIndex(final ID<?, ?> indexId) throws StorageException {
    getIndex(indexId).clear();
    try {
      IndexInfrastructure.rewriteVersion(IndexInfrastructure.getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void indexUnsavedDocuments() throws StorageException {
    myChangedFilesUpdater.forceUpdate();
    
    final FileDocumentManager fdManager = FileDocumentManager.getInstance();
    final Document[] documents = fdManager.getUnsavedDocuments();
    if (documents.length > 0) {
      // now index unsaved data
      setDataBufferingEnabled(true);
      myUnsavedDataIndexingSemaphore.down();
      try {
        for (Document document : documents) {
          final VirtualFile vFile = fdManager.getFile(document);
          if (!vFile.isValid() || !(vFile instanceof VirtualFileWithId)) {
            continue; // since the corresponding file is invalid, the document should be ignored
          }

          // Do not index content until document is committed. It will cause problems for indices, that depend on PSI otherwise.
          if (isUncomitted(document)) continue;

          final long currentDocStamp = document.getModificationStamp();
          if (currentDocStamp != getLastIndexedStamp(document).getAndSet(currentDocStamp)) {
            CharSequence lastIndexed = myLastIndexedUnsavedContent.get(document);
            if (lastIndexed == null) {
              lastIndexed = loadContent(vFile);
            }
            final FileContent oldFc = new FileContent(vFile, lastIndexed);
            final FileContent newFc = new FileContent(vFile, document.getText());
            for (ID<?, ?> indexId : myIndices.keySet()) {
              if (getInputFilter(indexId).acceptInput(vFile)) {
                final int inputId = Math.abs(getFileId(vFile));
                getIndex(indexId).update(inputId, newFc, oldFc);
              }
            }
            myLastIndexedUnsavedContent.put(document, newFc.getContentAsText());
          }
        }
      }
      finally {
        myUnsavedDataIndexingSemaphore.up();
        myUnsavedDataIndexingSemaphore.waitFor(); // may need to wait until another thread is done with indexing 
      }
    }
  }

  @NotNull
  private AtomicLong getLastIndexedStamp(final Document document) {
    AtomicLong lastStamp;
    synchronized (myLastIndexedDocStamps) {
      lastStamp = myLastIndexedDocStamps.get(document);
      if (lastStamp == null) {
        lastStamp = new AtomicLong(0L);
        myLastIndexedDocStamps.put(document, lastStamp);
      }
    }
    return lastStamp;
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
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(new ID(s)));
    }
  }

  public void requestRebuild(ID<?, ?> indexId) {
    myRebuildStatus.get(indexId).set(REQUIRES_REBUILD);
  }
  
  @Nullable
  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  @Nullable
  private InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }

  public void indexFileContent(com.intellij.ide.startup.FileContent content) {
    final VirtualFile file = content.getVirtualFile();
    FileContent fc = null;
    FileContent oldContent = null;
    final byte[] oldBytes = myFileContentAttic.remove(file);
 
    for (ID<?, ?> indexId : myIndices.keySet()) {
      if (getInputFilter(indexId).acceptInput(file)) {
        if (fc == null) {
          byte[] currentBytes;
          try {
            currentBytes = content.getBytes();
          }
          catch (IOException e) {
            currentBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
          }
          fc = new FileContent(file, currentBytes);
          oldContent = oldBytes != null? new FileContent(file, oldBytes) : null;
        }
        try {
          updateSingleIndex(indexId, file, fc, oldContent);
        }
        catch (StorageException e) {
          requestRebuild(indexId);
          LOG.info(e);
        }
      }
    }
  }

  private void updateSingleIndex(final ID<?, ?> indexId, final VirtualFile file, final FileContent currentFC, final FileContent oldFC)
    throws StorageException {

    setDataBufferingEnabled(false);
    synchronized (myLastIndexedDocStamps) {
      myLastIndexedDocStamps.clear();
      myLastIndexedUnsavedContent.clear();
    }

    final int inputId = Math.abs(getFileId(file));
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    if (CachesBasedRefSearcher.DEBUG) {
      System.out.println("FileBasedIndex.updateSingleIndex");
      System.out.println("indexId = " + indexId);
      System.out.println("Indexing inputId = " + inputId);
      System.out.println("file = " + file.getPresentableUrl());
      System.out.println("IndexInfrastructure.getIndexRootDir(indexId) = " + IndexInfrastructure.getIndexRootDir(indexId));
    }


    index.update(inputId, currentFC, oldFC);
    if (file.isValid()) {
      if (currentFC != null) {
        IndexingStamp.update(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId));
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

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file);
  }

  private static CharSequence loadContent(VirtualFile file) {
    return LoadTextUtil.loadText(file, true);
  }

  private final class ChangedFilesUpdater extends VirtualFileAdapter implements CacheUpdater{
    private final Set<VirtualFile> myFilesToUpdate = Collections.synchronizedSet(new HashSet<VirtualFile>());
    private final BlockingQueue<FutureTask<?>> myFutureInvalidations = new LinkedBlockingQueue<FutureTask<?>>();
    // No need to react on movement events since files stay valid, their ids don't change and all associated attributes remain intact.

    public void fileCreated(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void fileDeleted(final VirtualFileEvent event) {
      myFilesToUpdate.remove(event.getFile()); // no need to update it anymore
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      markDirty(event);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile(), false);
    }

    public void beforeContentsChange(final VirtualFileEvent event) {
      scheduleInvalidation(event.getFile(), true); 
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        final VirtualFile file = event.getFile();
        if (!file.isDirectory()) {
          scheduleInvalidation(file, false);
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
                myFilesToUpdate.add(file);
              }
              else {
                try {
                  updateSingleIndex(indexId, file, new FileContent(file, (byte[])null), null);
                }
                catch (StorageException e) {
                  LOG.info(e);
                  requestRebuild(indexId);
                }
              }
            }
          }
          return true;
        }
      });
    }

    private void scheduleInvalidation(final VirtualFile file, final boolean saveContent) {
      if (file.isDirectory()) {
        if (areChildrenLoaded(file)) {
          for (VirtualFile child : file.getChildren()) {
            scheduleInvalidation(child, saveContent); 
          }
        }
      }
      else {
        final List<ID<?, ?>> affectedIndices = new ArrayList<ID<?, ?>>(myIndices.size());
        for (ID<?, ?> indexId : myIndices.keySet()) {
          if (shouldUpdateIndex(file, indexId)) {
            if (myNeedContentLoading.contains(indexId)) {
              affectedIndices.add(indexId);
            }
            else {
              // invalidate it synchronously
              try {
                updateSingleIndex(indexId, file, null, new FileContent(file, (byte[])null));
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
        }
        
        if (affectedIndices.size() > 0) {
          if (saveContent) {
            myFileContentAttic.offer(file);
          }
          else {
            // first check if there is an unprocessed content from previous events
            byte[] content = myFileContentAttic.remove(file);
            try {
              if (content == null) {
                content = file.contentsToByteArray();
              }
            }
            catch (IOException e) {
              content = ArrayUtil.EMPTY_BYTE_ARRAY;
            }
            final FileContent fc = new FileContent(file, content);
            final FutureTask<?> future = (FutureTask<?>)myInvalidationService.submit(new Runnable() {
              public void run() {
                for (ID<?, ?> indexId : affectedIndices) {
                  try {
                    updateSingleIndex(indexId, file, null, fc);
                  }
                  catch (StorageException e) {
                    LOG.info(e);
                    requestRebuild(indexId);
                  }
                }
              }
            });
            myFutureInvalidations.offer(future);
          }
          iterateIndexableFiles(file, new Processor<VirtualFile>() {
            public boolean process(final VirtualFile file) {
              myFilesToUpdate.add(file);
              return true;
            }
          });
        }
      }
    }

    private boolean areChildrenLoaded(final VirtualFile file) {
      // todo: remove this check when the right VFS method is used
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return true;
      }
      if (isMock(file)) {
        return true;
      }
      // TODO: ManagingFS.areChildrenLoaded(file) is not the right method to use here
      //  need API from VFS to understand if there were _any_ child virtual files created for the dir 
      return ManagingFS.getInstance().areChildrenLoaded(file);
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
      synchronized (myFilesToUpdate) {
        return myFilesToUpdate.toArray(new VirtualFile[myFilesToUpdate.size()]);
      }
    }

    public void processFile(final com.intellij.ide.startup.FileContent fileContent) {
      ensureAllInvalidateTasksCompleted();
      processFileImpl(fileContent);
    }

    private final Semaphore myForceUpdateSemaphore = new Semaphore();
    
    public void forceUpdate() {
      ensureAllInvalidateTasksCompleted();
      myForceUpdateSemaphore.down();
      try {
        for (VirtualFile file: queryNeededFiles()) {
          processFileImpl(new com.intellij.ide.startup.FileContent(file));
        }
      }
      finally {
        myForceUpdateSemaphore.up();
        myForceUpdateSemaphore.waitFor(); // possibly wait until another thread completes indexing
      }
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
    
    private void processFileImpl(final com.intellij.ide.startup.FileContent fileContent) {
      final VirtualFile file = fileContent.getVirtualFile();
      final boolean reallyRemoved = myFilesToUpdate.remove(file);
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
              updateSingleIndex(indexId, file, new FileContent(file, (byte[])null), null);
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
  }

  private boolean shouldUpdateIndex(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) && (isMock(file) || IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private static boolean isMock(final VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  private boolean shouldIndexFile(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) && (isMock(file) || !IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
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

  private static boolean isUncomitted(Document doc) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (PsiDocumentManager.getInstance(project).isUncommited(doc)) {
        return true;
      }
    }

    return false;
  }
  
  private static class IndexableFilesFilter implements InputFilter {
    private final InputFilter myDelegate;

    private IndexableFilesFilter(InputFilter delegate) {
      myDelegate = delegate;
    }

    public boolean acceptInput(final VirtualFile file) {
      return (file instanceof VirtualFileWithId) && myDelegate.acceptInput(file);
    }
  }
}
