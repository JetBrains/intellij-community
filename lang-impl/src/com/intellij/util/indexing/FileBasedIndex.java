package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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

  private final Map<String, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<String, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final CompositeInputFiler myCompositeFilter = new CompositeInputFiler();
  private FileBasedIndexState myPreviouslyRegistered;

  private TObjectLongHashMap<String> myIndexIdToCreationStamp = new TObjectLongHashMap<String>();
  private TObjectIntHashMap<String> myIndexIdToVersionMap = new TObjectIntHashMap<String>();

  private Map<Document, Pair<CharSequence, Long>> myIndexingHistory = Collections.synchronizedMap(new HashMap<Document, Pair<CharSequence, Long>>());
  
  private List<Disposable> myDisposables = new ArrayList<Disposable>();

  private CompositeCommand myStartDataBuffering = new CompositeCommand();
  private CompositeCommand myStopDataBuffering = new CompositeCommand();
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private ChangedFilesUpdater myChangedFilesUpdater;

  private List<IndexableFileSet> myIndexableSets = new CopyOnWriteArrayList<IndexableFileSet>();
  private Set<String> myRequiresRebuild = Collections.synchronizedSet(new HashSet<String>());

  public static interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }

  public static final class FileContent {
    public final VirtualFile file;
    public final CharSequence content;

    public FileContent(final VirtualFile file, final CharSequence content) {
      this.file = file;
      this.content = content;
    }
  }

  public FileBasedIndex(final VirtualFileManagerEx vfManager) throws IOException {
    final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
    for (FileBasedIndexExtension extension : extensions) {
      registerIndexer(
        extension.getName(),
        extension.getIndexer(),
        extension.getKeyDescriptor(),
        extension.getValueExternalizer(),
        extension.getInputFilter(), 
        extension.getVersion()
      );
    }

    dropUnregisteredIndices();

    myChangedFilesUpdater = new ChangedFilesUpdater();
    vfManager.addVirtualFileListener(myChangedFilesUpdater);
    vfManager.registerRefreshUpdater(myChangedFilesUpdater);
    myDisposables.add(new Disposable() {
      public void dispose() {
        vfManager.removeVirtualFileListener(myChangedFilesUpdater);
        vfManager.unregisterRefreshUpdater(myChangedFilesUpdater);
      }
    });
  }

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
  }

  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted
   */
  private <K, V> void registerIndexer(final String name, final DataIndexer<K, V, FileContent> indexer, final PersistentEnumerator.DataDescriptor<K> keyDescriptor,
                                      final DataExternalizer<V> valueExternalizer,
                                      final InputFilter filter,
                                      final int version) throws IOException {
    myIndexIdToVersionMap.put(name, version);
    final File versionFile = getVersionFile(name);
    if (readVersion(versionFile) != version || ourUnitTestMode) { // in test mode drop caches each time application starts
      FileUtil.delete(getIndexRootDir(name));
      rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(getStorageFile(name), keyDescriptor, valueExternalizer);
        myDisposables.add(new Disposable() {
          public void dispose() {
            try {
              storage.close();
            }
            catch (StorageException e) {
              LOG.error(e);
            }
          }
        });

        final MemoryIndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        myStartDataBuffering.addTask(new Runnable() {
          public void run() {
            memStorage.setBufferingEnabled(true);
          }
        });
        myStopDataBuffering.addTask(new Runnable() {
          public void run() {
            memStorage.setBufferingEnabled(false);
          }
        });

        final MapReduceIndex<?, ?, FileContent> index = new MapReduceIndex<K, V, FileContent>(indexer, memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, filter));
        myCompositeFilter.addFilter(filter);
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
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
  }
                
  public FileBasedIndexState getState() {
    return new FileBasedIndexState(myIndices.keySet());
  }

  public void loadState(final FileBasedIndexState state) {
    myPreviouslyRegistered = state;
  }

  @NotNull
  public <K, V> List<V> getData(final String indexId, K dataKey, Project project) {
    return getData(indexId, dataKey, project, null);
  }

  
  public static interface DataFilter<K, V> {
    List<V> process(K key, ValueContainer<V> container);
  }
  
  @NotNull
  public <K, V> List<V> getData(final String indexId, K dataKey, Project project, @Nullable DataFilter<K, V> filter) {
    checkRebuild(indexId);
    try {
      indexUnsavedDocuments();
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return Collections.emptyList();
      }
      
      final Lock readLock = index.getReadLock();
      readLock.lock();
      try {
        final ValueContainer<V> container = index.getData(dataKey);
        final List<V> valueList = filter != null? filter.process(dataKey, container) : container.toValueList();

        if (project != null) {
          final DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
          final PersistentFS fs = (PersistentFS)PersistentFS.getInstance();
    
          for (Iterator<V> it = valueList.iterator(); it.hasNext();) {
            final V value = it.next();
            if (!belongsToProject(container.getInputIdsIterator(value), dirIndex, fs)) {
              it.remove();
            }
          }
        }

        return valueList;
      }
      finally {
        readLock.unlock();
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  public <K> Collection<VirtualFile> getContainingFiles(final String indexId, K dataKey, @NotNull Project project) {
    checkRebuild(indexId);
    try {
      indexUnsavedDocuments();
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (index == null) {
        return Collections.emptyList();
      }

      final List<VirtualFile> files = new ArrayList<VirtualFile>();
      final TIntHashSet processedIds = new TIntHashSet();
      
      final DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
      final PersistentFS fs = (PersistentFS)PersistentFS.getInstance();

      final Lock readLock = index.getReadLock();
      readLock.lock();
      try {
        final ValueContainer container = index.getData(dataKey);
        for (Iterator it = container.getValueIterator(); it.hasNext();) {
          final Object value = it.next();
          //noinspection unchecked
          final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value);
          while (inputIdsIterator.hasNext()) {
            final int id = inputIdsIterator.next();
            if (!processedIds.contains(id)) {
              processedIds.add(id);
              VirtualFile file = findFileById(dirIndex, fs, id);
              if (file != null) {
                files.add(file);
              }
            }
          }
        }
      }
      finally {
        readLock.unlock();
      }

      return files;
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  private void checkRebuild(final String indexId) {
    final boolean reallyRemoved = myRequiresRebuild.remove(indexId);
    if (!reallyRemoved) {
      return;
    }
    dropIndex(indexId);
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
    new Task.Modal(null, "Updating index", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        synchronizer.execute();
      }
    }.queue();
  }

  @Nullable
  private static VirtualFile findFileById(final DirectoryIndex dirIndex, final PersistentFS fs, final int id) {
    final boolean isDirectory = fs.isDirectory(id);
    final DirectoryInfo directoryInfo = isDirectory ? dirIndex.getInfoForDirectoryId(id) : dirIndex.getInfoForDirectoryId(fs.getParent(id));
    if (directoryInfo != null && (directoryInfo.contentRoot != null || directoryInfo.sourceRoot != null)) {
      if (isDirectory) {
        return directoryInfo.directory;
      }
      else {
        final VirtualFile child = directoryInfo.directory.findChild(fs.getName(id));
        if (child != null) {
          return child;
        }
      }
    }

    return findTestFile(id);
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
      myStartDataBuffering.execute();
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
          for (String indexId : myIndices.keySet()) {
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

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (String key : myIndices.keySet()) {
      indicesToDrop.remove(key);
    }
    for (String s : indicesToDrop) {
      dropIndex(s);
    }
  }

  private void dropIndex(String indexId) {
    FileUtil.delete(getIndexRootDir(indexId));
    myIndexIdToCreationStamp.remove(indexId);
  }
  
  public void requestRebuild(String indexId) {
    myRequiresRebuild.add(indexId);
  }
  
  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(String indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  private InputFilter getInputFilter(String indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }

  private static boolean belongsToProject(final ValueContainer.IntIterator inputIdsIterator, final DirectoryIndex dirIndex, final PersistentFS fs) {
    while (inputIdsIterator.hasNext()) {
      final int id = inputIdsIterator.next();
      final DirectoryInfo directoryInfo = fs.isDirectory(id)?
                                          dirIndex.getInfoForDirectoryId(id) :
                                          dirIndex.getInfoForDirectoryId(fs.getParent(id));
      if (directoryInfo != null && directoryInfo.contentRoot != null) {
        return true; // the directory is under the content
      }
    }
    return false;
  }

  private long getIndexCreationStamp(String indexName) {
    long stamp = myIndexIdToCreationStamp.get(indexName);
    if (stamp <= 0) {
      stamp = getVersionFile(indexName).lastModified();
      myIndexIdToCreationStamp.put(indexName, stamp);
    }
    return stamp;
  }
  
  public static File getVersionFile(final String indexName) {
    return new File(getIndexRootDir(indexName), indexName + ".ver");
  }

  public static File getStorageFile(final String indexName) {
    return new File(getIndexRootDir(indexName), indexName);
  }

  public static File getIndexRootDir(final String indexName) {
    final File indexDir = new File(getPersistenceRoot(), indexName.toLowerCase(Locale.US));
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
    try {
      indexFile(file, content);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void indexFile(final VirtualFile file, @NotNull final com.intellij.ide.startup.FileContent content) throws IOException {
    FileContent fc = null;
    for (String indexId : myIndices.keySet()) {
      if (!IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId)) && getInputFilter(indexId).acceptInput(file)) {
        if (fc == null) {
          fc = new FileContent(file, LoadTextUtil.getTextByBinaryPresentation(content.getBytes(), file, false));
        }

        try {
          updateSingleIndex(indexId, file, fc, null);
        }
        catch (StorageException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void updateSingleIndex(final String indexId, final VirtualFile file, final FileContent currentFC, final FileContent oldFC)
    throws StorageException {

    myStopDataBuffering.execute();
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
    return LoadTextUtil.loadText(file);
  }

  private static final class CompositeInputFiler implements InputFilter {
    private final Set<InputFilter> myFilters = new HashSet<InputFilter>();

    public void addFilter(InputFilter filter) {
      myFilters.add(filter);
    }

    public void removeFilter(InputFilter filter) {
      myFilters.remove(filter);
    }

    public boolean acceptInput(final VirtualFile file) {
      if (file.isDirectory()) return false;
      for (InputFilter filter : myFilters) {
        if (filter.acceptInput(file)) {
          return true;
        }
      }
      return false;
    }
  }

  private final class ChangedFilesUpdater extends VirtualFileAdapter implements CacheUpdater{
    private final Set<VirtualFile> myFileToUpdate = Collections.synchronizedSet(new HashSet<VirtualFile>());
    private int myInvalidateTasksInProgress = 0;
    private final Object myLock = new Object();
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

    private void markDirty(final VirtualFileEvent event) {
      final VirtualFile file = event.getFile();
      includeToUpdateSet(file);
    }

    private void scheduleInvalidation(final VirtualFile file) {
      final List<Runnable> tasks = new ArrayList<Runnable>();
      invalidateIndex(file, tasks);
      if (tasks.size() > 0) {
        adjustInvalidateTasksCount(tasks.size());
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            for (Runnable task : tasks) {
              task.run();
            }
          }
        });
      }
    }
    
    private void invalidateIndex(final VirtualFile file, final List<Runnable> tasks) {
      if (file.isDirectory()) {
        if (ManagingFS.getInstance().areChildrenLoaded(file)) {
          for (VirtualFile child : file.getChildren()) {
            invalidateIndex(child, tasks);
          }
        }
      }
      else {
        final List<String> toUpdate = new ArrayList<String>(myIndices.size());
        for (String indexId : myIndices.keySet()) {
          if (getInputFilter(indexId).acceptInput(file) && IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId))) {
            toUpdate.add(indexId);
          }
        }
        
        if (toUpdate.size() > 0) {
          includeToUpdateSet(file);
          final FileContent fc = new FileContent(file, loadContent(file));
          tasks.add(new Runnable() {
            public void run() {
              try {
                for (String indexId : toUpdate) {
                  try {
                    updateSingleIndex(indexId, file, null, fc);
                  }
                  catch (StorageException e) {
                    LOG.error(e);
                  }
                }
              }
              finally {
                adjustInvalidateTasksCount(-1);
              }
            }
          });
        }
      }
    }

    private void adjustInvalidateTasksCount(int delta) {
      synchronized (myLock) {
        myInvalidateTasksInProgress += delta;
        if (myInvalidateTasksInProgress == 0) {
          myLock.notifyAll();
        }
      }
    }
    
    private void ensureAllInvalidateTasksCompleted() {
      synchronized (myLock) {
        while (myInvalidateTasksInProgress != 0) {
          try {
            myLock.wait();
          }
          catch (InterruptedException ignored) {
          }
        }
      }
    }

    private void includeToUpdateSet(final VirtualFile file) {
      if (file.isDirectory()) {
        ContentIterator iterator = new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              myFileToUpdate.add(fileOrDir);
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
            myFileToUpdate.add(file);
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
  
  private static class CompositeCommand {
    private final List<Runnable> myTasks = new ArrayList<Runnable>();
    
    void addTask(Runnable task) {
      myTasks.add(task);
    }
    
    void execute() {
      for (Runnable task : myTasks) {
        task.run();
      }
    }

    void clear() {
      myTasks.clear();
    }
  }

  private class UnindexedFilesFinder implements CollectingContentIterator {
    private final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    private final Collection<String> myIndexIds;

    public UnindexedFilesFinder(final Collection<String> indexIds) {
      myIndexIds = indexIds;
    }

    public List<VirtualFile> getFiles() {
      return myFiles;
    }

    public boolean processFile(final VirtualFile file) {
      if (!file.isDirectory()) {
        for (String indexId : myIndexIds) {
          if (!IndexingStamp.isFileIndexed(file, indexId, getIndexCreationStamp(indexId)) && getInputFilter(indexId).acceptInput(file)) {
            myFiles.add(file);
            break;
          }
        }
      }
      return true;
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
