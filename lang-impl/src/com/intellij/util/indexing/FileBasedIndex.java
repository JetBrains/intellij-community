package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
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
  
  private static final int VERSION = 3;

  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNeedContentLoading = new HashSet<ID<?, ?>>();
  private FileBasedIndexState myPreviouslyRegistered;

  private final TObjectLongHashMap<ID<?, ?>> myIndexIdToCreationStamp = new TObjectLongHashMap<ID<?, ?>>();

  private final Map<Document, AtomicLong> myLastIndexedDocStamps = new HashMap<Document, AtomicLong>();
  private final Map<Document, CharSequence> myLastIndexedUnsavedContent = new HashMap<Document, CharSequence>();
  
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  private ChangedFilesUpdater myChangedFilesUpdater;

  private final List<IndexableFileSet> myIndexableSets = new CopyOnWriteArrayList<IndexableFileSet>();
  private final Set<ID<?, ?>> myRequiresRebuild = Collections.synchronizedSet(new HashSet<ID<?, ?>>());

  private final ExecutorService myInvalidationService = ConcurrencyUtil.newSingleThreadExecutor("FileBasedIndex.InvalidationQueue");
  private final VirtualFileManagerEx myVfManager;
  private final Semaphore myUnsavedDataIndexingSemaphore = new Semaphore();

  private final FileContentStorage myFileContentAttic;
  
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
    myFileContentAttic = new FileContentStorage(new File(getPersistenceRoot(), "updates.tmp"));
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
    if (versionDiffers(versionFile, version)) {
      FileUtil.delete(getIndexRootDir(name));
      rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer());
        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(extension, memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, extension.getInputFilter()));
        break;
      }
      catch (IOException e) {
        FileUtil.delete(getIndexRootDir(name));
        rewriteVersion(versionFile, version);
      }
    }
  }

  private static <K, V> UpdatableIndex<K, V, FileContent> createIndex(final FileBasedIndexExtension<K, V> extension, final MemoryIndexStorage<K, V> memStorage) {
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      return ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension).createIndexImplementation(memStorage);
    }
    else {
      return new MapReduceIndex<K, V, FileContent>(extension.getIndexer(), memStorage);
    }
  }

  private static boolean versionDiffers(final File versionFile, final int currentIndexVersion) {
    try {
      final DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
      try {
        final int savedIndexVersion = in.readInt();
        final int commonVersion = in.readInt();
        return (savedIndexVersion != currentIndexVersion) || (commonVersion != VERSION); 
      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      return true;
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
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        requestRebuild(indexId);
        LOG.error(e);
      }
      else {
        throw e;
      }
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

    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      new Task.Modal(null, "Updating index", false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          synchronizer.execute();
        }
      }.queue();
    }
    else {
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      Runnable runnable = new Runnable() {
        public void run() {
          // due to current assertions in 'progress management' subsystem 
          // we have to queue the task only from event dispatch thread
          // when these are solved, it is ok to remove invokeLater() calls from here
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
        }
      };
      if (application.isUnitTestMode()) {
        application.invokeLater(runnable, ModalityState.NON_MODAL);
      }
      else {
        SwingUtilities.invokeLater(runnable);
      }
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
      myUnsavedDataIndexingSemaphore.down();
      try {
        for (Document document : documents) {
          final VirtualFile vFile = fdManager.getFile(document);
          if (!vFile.isValid()) {
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
            myLastIndexedUnsavedContent.put(document, newFc.content);
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
      FileUtil.delete(getIndexRootDir(new ID(s)));
    }
  }

  public void requestRebuild(ID<?, ?> indexId) {
    myRequiresRebuild.add(indexId);
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

  private long getIndexCreationStamp(ID<?, ?> indexName) {
    long stamp = myIndexIdToCreationStamp.get(indexName);
    if (stamp <= 0) {
      stamp = getVersionFile(indexName).lastModified();
      myIndexIdToCreationStamp.put(indexName, stamp);
    }
    return stamp;
  }
  
  private static File getVersionFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName + ".ver");
  }

  private static File getStorageFile(final ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.toString());
  }

  private static File getIndexRootDir(final ID<?, ?> indexName) {
    final File indexDir = new File(getPersistenceRoot(), indexName.toString().toLowerCase(Locale.US));
    indexDir.mkdirs();
    return indexDir;
  }

  private static File getPersistenceRoot() {
    File file = new File(PathManager.getSystemPath(), "index");
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
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
      os.writeInt(VERSION);
    }
    finally {
      myIndexIdToCreationStamp.clear();
      os.close();
    }
  }

  public void indexFileContent(com.intellij.ide.startup.FileContent content) {
    final VirtualFile file = content.getVirtualFile();
    FileContent fc = null;
    FileContent oldContent = null;
    final byte[] bytes = myFileContentAttic.remove(file);
 
    for (ID<?, ?> indexId : myIndices.keySet()) {
      if (getInputFilter(indexId).acceptInput(file)) {
        if (fc == null) {
          fc = new FileContent(file, CacheUtil.getContentText(content));
          oldContent = bytes != null? new FileContent(file, LoadTextUtil.getTextByBinaryPresentation(bytes, file, false)) : null;
        }
        try {
          updateSingleIndex(indexId, file, fc, oldContent);
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
    synchronized (myLastIndexedDocStamps) {
      myLastIndexedDocStamps.clear();
      myLastIndexedUnsavedContent.clear();
    }

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

    private void scheduleInvalidation(final VirtualFile file, final boolean saveContent) {
      if (file.isDirectory()) {
        if (!(file instanceof NewVirtualFile) || ManagingFS.getInstance().areChildrenLoaded(file)) {
          for (VirtualFile child : file.getChildren()) {
            scheduleInvalidation(child, false);
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
              myFilesToUpdate.add(file);
              return true;
            }
          });
          if (saveContent) {
            myFileContentAttic.offer(file);
          }
          else {
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

  private static boolean isUncomitted(Document doc) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (PsiDocumentManager.getInstance(project).isUncommited(doc)) {
        return true;
      }
    }

    return false;
  }
}
