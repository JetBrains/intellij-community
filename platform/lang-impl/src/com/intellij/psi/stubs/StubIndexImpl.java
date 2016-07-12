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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

@State(name = "FileBasedIndex", storages = @Storage(value = "stubIndex.xml", roamingType = RoamingType.DISABLED))
public class StubIndexImpl extends StubIndex implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  private static final AtomicReference<Boolean> ourForcedClean = new AtomicReference<>(null);
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubIndexImpl");

  private static class AsyncState {
    private final Map<StubIndexKey<?, ?>, MyIndex<?>> myIndices = new THashMap<>();
    private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<>();
  }

  private final StubProcessingHelper myStubProcessingHelper;
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();
  private volatile Future<AsyncState> myStateFuture;
  private volatile AsyncState myState;
  private volatile boolean myInitialized;

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl(FileBasedIndex fileBasedIndex /* need this to ensure initialization order*/ ) throws IOException {
    myStubProcessingHelper = new StubProcessingHelper(fileBasedIndex);
  }

  @Nullable
  static StubIndexImpl getInstanceOrInvalidate() {
    if (ourForcedClean.compareAndSet(null, Boolean.TRUE)) {
      return null;
    }
    return (StubIndexImpl)getInstance();
  }

  private AsyncState getAsyncState() {
    //if (!myInitialized) { // memory barrier
    //  //throw new IndexNotReadyException();
    //  LOG.error("Unexpected initialization problem");
    //}
    AsyncState state = myState; // memory barrier
    if (state == null) {
      try {
        myState = state = myStateFuture.get();
      } catch(Throwable t) {
        throw new RuntimeException(t);
      }
    }
    return state;
  }

  private static <K> boolean registerIndexer(@NotNull final StubIndexExtension<K, ?> extension, final boolean forceClean, AsyncState state) throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    synchronized (state) {
      state.myIndexIdToVersionMap.put(indexKey, version);
    }
    final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
    final boolean versionFileExisted = versionFile.exists();
    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    boolean needRebuild = false;
    if (forceClean || IndexingStamp.versionDiffers(versionFile, version)) {
      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      boolean indexRootHasChildren = children != null && children.length > 0;
      needRebuild = !forceClean && (versionFileExisted || indexRootHasChildren);
      if (needRebuild) {
        LOG.info("Version has changed for stub index " + extension.getKey() + ". The index will be rebuilt.");
      }
      if (indexRootHasChildren) FileUtil.deleteWithRenaming(indexRootDir);
      IndexingStamp.rewriteVersion(versionFile, version); // todo snapshots indices
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, StubIdList> storage = new MapIndexStorage<>(
          IndexInfrastructure.getStorageFile(indexKey),
          extension.getKeyDescriptor(),
          StubIdExternalizer.INSTANCE,
          extension.getCacheSize(),
          false,
          extension instanceof StringStubIndexExtension && ((StringStubIndexExtension)extension).traceKeyHashToVirtualFileMapping()
        );

        final MemoryIndexStorage<K, StubIdList> memStorage = new MemoryIndexStorage<>(storage, indexKey);
        MyIndex<K> index = new MyIndex<>(new IndexExtension<K, StubIdList, Void>() {
          @NotNull
          @Override
          public ID<K, StubIdList> getName() {
            return (ID<K, StubIdList>)indexKey;
          }

          @NotNull
          @Override
          public DataIndexer<K, StubIdList, Void> getIndexer() {
            return inputData -> Collections.emptyMap();
          }

          @NotNull
          @Override
          public KeyDescriptor<K> getKeyDescriptor() {
            return extension.getKeyDescriptor();
          }

          @NotNull
          @Override
          public DataExternalizer<StubIdList> getValueExternalizer() {
            return StubIdExternalizer.INSTANCE;
          }

          @Override
          public int getVersion() {
            return extension.getVersion();
          }
        }, memStorage);
        synchronized (state) {
          state.myIndices.put(indexKey, index);
        }
        break;
      }
      catch (IOException e) {
        needRebuild = true;
        onExceptionInstantiatingIndex(version, versionFile, indexRootDir, e);
      } catch (RuntimeException e) {
        //noinspection ThrowableResultOfMethodCallIgnored
        Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
        if (cause == null) throw e;
        onExceptionInstantiatingIndex(version, versionFile, indexRootDir, e);
      }
    }
    return needRebuild;
  }

  private static void onExceptionInstantiatingIndex(int version, File versionFile, File indexRootDir, Exception e) throws IOException {
    LOG.info(e);
    FileUtil.deleteWithRenaming(indexRootDir);
    IndexingStamp.rewriteVersion(versionFile, version); // todo snapshots indices
  }

  public long getIndexModificationStamp(StubIndexKey<?, ?> indexId, @NotNull Project project) {
    MyIndex<?> index = getAsyncState().myIndices.get(indexId);
    if (index != null) {
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));
      return index.getModificationStamp();
    }
    return -1;
  }

  public void flush() throws StorageException {
    if (!myInitialized) {
      return;
    }
    AsyncState state = getAsyncState();
    for (StubIndexKey key : getAllStubIndexKeys()) {
      final MyIndex<?> index = state.myIndices.get(key);
      index.flush();
    }
  }

  private static class StubIdExternalizer implements DataExternalizer<StubIdList> {
    private static final StubIdExternalizer INSTANCE = new StubIdExternalizer();

    @Override
    public void save(@NotNull final DataOutput out, @NotNull final StubIdList value) throws IOException {
      int size = value.size();
      if (size == 0) {
        DataInputOutputUtil.writeINT(out, Integer.MAX_VALUE);
      }
      else if (size == 1) {
        DataInputOutputUtil.writeINT(out, value.get(0)); // most often case
      }
      else {
        DataInputOutputUtil.writeINT(out, -size);
        for(int i = 0; i < size; ++i) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }
    }

    @NotNull
    @Override
    public StubIdList read(@NotNull final DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      if (size == Integer.MAX_VALUE) {
        return new StubIdList();
      }
      else if (size >= 0) {
        return new StubIdList(size);
      }
      else {
        size = -size;
        int[] result = new int[size];
        for(int i = 0; i < size; ++i) {
          result[i] = DataInputOutputUtil.readINT(in);
        }
        return new StubIdList(result, size);
      }
    }
  }

  public <K> void serializeIndexValue(DataOutput out, StubIndexKey<K, ?> stubIndexKey, Map<K, StubIdList> map) throws IOException {
    MyIndex<K> index = (MyIndex<K>)getAsyncState().myIndices.get(stubIndexKey);
    KeyDescriptor<K> keyDescriptor = index.getExtension().getKeyDescriptor();

    DataInputOutputUtil.writeINT(out, map.size());
    for(K key:map.keySet()) {
      keyDescriptor.save(out, key);
      StubIdExternalizer.INSTANCE.save(out, map.get(key));
    }
  }

  public <K> Map<K, StubIdList> deserializeIndexValue(DataInput in, StubIndexKey<K, ?> stubIndexKey) throws IOException {
    MyIndex<K> index = (MyIndex<K>)getAsyncState().myIndices.get(stubIndexKey);
    KeyDescriptor<K> keyDescriptor = index.getExtension().getKeyDescriptor();
    int mapSize = DataInputOutputUtil.readINT(in);

    Map<K, StubIdList> result = new THashMap<>(mapSize);
    for(int i = 0; i < mapSize; ++i) {
      K key = keyDescriptor.read(in);
      StubIdList read = StubIdExternalizer.INSTANCE.read(in);
      result.put(key, read);
    }
    return result;
  }

  @NotNull
  @Override
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull final Key key,
                                                           @NotNull final Project project,
                                                           @Nullable final GlobalSearchScope scope) {
    return get(indexKey, key, project, scope, null);
  }

  @Override
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull Key key,
                                                           @NotNull Project project,
                                                           @Nullable GlobalSearchScope scope,
                                                           IdFilter filter) {
    final List<Psi> result = new SmartList<>();
    process(indexKey, key, project, scope, filter, Processors.cancelableCollectProcessor(result));
    return result;
  }

  @Override
  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                       @NotNull Key key,
                                                       @NotNull Project project,
                                                       @Nullable GlobalSearchScope scope,
                                                       Class<Psi> requiredClass,
                                                       @NotNull Processor<? super Psi> processor) {
    return processElements(indexKey, key, project, scope, null, requiredClass, processor);
  }

  @Override
  public <Key, Psi extends PsiElement> boolean processElements(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                       @NotNull final Key key,
                                                       @NotNull final Project project,
                                                       @Nullable final GlobalSearchScope scope,
                                                       @Nullable IdFilter idFilter,
                                                       @NotNull final Class<Psi> requiredClass,
                                                       @NotNull final Processor<? super Psi> processor) {
    return doProcessStubs(indexKey, key, project, scope, new StubIdListContainerAction(idFilter, project) {
      final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
      @Override
      protected boolean process(int id, StubIdList value) {
        final VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
        if (file == null || scope != null && !scope.contains(file)) {
          return true;
        }
        return myStubProcessingHelper.processStubsInFile(project, file, value, processor, requiredClass);
      }
    });
  }

  private <Key> boolean doProcessStubs(@NotNull final StubIndexKey<Key, ?> indexKey,
                                       @NotNull final Key key,
                                       @NotNull final Project project,
                                       @Nullable final GlobalSearchScope scope,
                                       @NotNull StubIdListContainerAction action) {
    final FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    fileBasedIndex.ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);

    final MyIndex<Key> index = (MyIndex<Key>)getAsyncState().myIndices.get(indexKey);

    try {
      myAccessValidator.checkAccessingIndexDuringOtherIndexProcessing(indexKey);

      try {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        FileBasedIndexImpl.disableUpToDateCheckForCurrentThread();

        index.getReadLock().lock();

        myAccessValidator.startedProcessingActivityForIndex(indexKey);

        return index.getData(key).forEach(action);
      }
      finally {
        myAccessValidator.stoppedProcessingActivityForIndex(indexKey);
        index.getReadLock().unlock();
        FileBasedIndexImpl.enableUpToDateCheckForCurrentThread();
      }
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    } catch (AssertionError ae) {
      forceRebuild(ae);
    }

    return true;
  }

  @Override
  public void forceRebuild(@NotNull Throwable e) {
    LOG.info(e);
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  private static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID);
  }

  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project) {
    Set<K> allKeys = ContainerUtil.newTroveSet();
    processAllKeys(indexKey, project, Processors.cancelableCollectProcessor(allKeys));
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project, Processor<K> processor) {
    return processAllKeys(indexKey, processor, GlobalSearchScope.allScope(project), null);
  }

  @Override
  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Processor<K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, scope.getProject(), scope);

    final MyIndex<K> index = (MyIndex<K>)getAsyncState().myIndices.get(indexKey);
    myAccessValidator.checkAccessingIndexDuringOtherIndexProcessing(indexKey);
    try {
      myAccessValidator.startedProcessingActivityForIndex(indexKey);
      return index.processAllKeys(processor, scope, idFilter);
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        forceRebuild(e);
      }
      throw e;
    } finally {
      myAccessValidator.stoppedProcessingActivityForIndex(indexKey);
    }
    return true;
  }

  @NotNull
  @Override
  public <Key> IdIterator getContainingIds(@NotNull StubIndexKey<Key, ?> indexKey,
                                           @NotNull Key dataKey,
                                           @NotNull final Project project,
                                           @NotNull final GlobalSearchScope scope) {
    final TIntArrayList result = new TIntArrayList();
    doProcessStubs(indexKey, dataKey, project, scope, new StubIdListContainerAction(null, project) {
      @Override
      protected boolean process(int id, StubIdList value) {
        result.add(id);
        return true;
      }
    });
    return new IdIterator() {
      int cursor;
      @Override
      public boolean hasNext() {
        return cursor < result.size();
      }

      @Override
      public int next() {
        return result.get(cursor++);
      }

      @Override
      public int size() {
        return result.size();
      }
    };
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  @Override
  public void initComponent() {
    long started = System.nanoTime();
    StubIndexExtension<?, ?>[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    LOG.info("All stub exts enumerated:" + (System.nanoTime() - started) / 1000000);
    started = System.nanoTime();

    myStateFuture = IndexInfrastructure.submitGenesisTask(new StubIndexInitialization(extensions));
    LOG.info("stub exts update scheduled:" + (System.nanoTime() - started) / 1000000);

    if (!IndexInfrastructure.ourDoAsyncIndicesInitialization) {
      try {
        myStateFuture.get();
      } catch (Throwable t) {
        LOG.error(t);
      }
    }
  }

  @Override
  public void disposeComponent() {
    // This index must be disposed only after StubUpdatingIndex is disposed
    // To ensure this, disposing is done explicitly from StubUpdatingIndex by calling dispose() method
    // do not call this method here to avoid double-disposal
  }

  public void dispose() {
    for (UpdatableIndex index : getAsyncState().myIndices.values()) {
      index.dispose();
    }
  }

  public void setDataBufferingEnabled(final boolean enabled) {
    for (UpdatableIndex index : getAsyncState().myIndices.values()) {
      final IndexStorage indexStorage = ((MapReduceIndex)index).getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
  }

  public void cleanupMemoryStorage() {
    for (UpdatableIndex index : getAsyncState().myIndices.values()) {
      final IndexStorage indexStorage = ((MapReduceIndex)index).getStorage();
      index.getWriteLock().lock();
      try {
        ((MemoryIndexStorage)indexStorage).clearMemoryMap();
      }
      finally {
        index.getWriteLock().unlock();
      }
    }
  }


  public void clearAllIndices() {
    for (UpdatableIndex index : getAsyncState().myIndices.values()) {
      try {
        index.clear();
      }
      catch (StorageException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }

  private void dropUnregisteredIndices(AsyncState state) {
    if (ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    final Set<String> indicesToDrop =
      new HashSet<>(myPreviouslyRegistered != null ? myPreviouslyRegistered.registeredIndices : Collections.emptyList());
    for (ID<?, ?> key : state.myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }

    if (!indicesToDrop.isEmpty()) {
      LOG.info("Dropping indices:" + StringUtil.join(indicesToDrop, ","));

      for (String s : indicesToDrop) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(StubIndexKey.createIndexKey(s)));
      }
    }
  }

  @Override
  public StubIndexState getState() {
    if (!myInitialized) return null;
    return new StubIndexState(getAsyncState().myIndices.keySet());
  }

  @Override
  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public final Lock getWriteLock(StubIndexKey indexKey) {
    return getAsyncState().myIndices.get(indexKey).getWriteLock();
  }

  Collection<StubIndexKey> getAllStubIndexKeys() {
    return Collections.unmodifiableCollection(getAsyncState().myIndices.keySet());
  }

  public <K> void updateIndex(@NotNull StubIndexKey key, int fileId, @NotNull final Map<K, StubIdList> oldValues, @NotNull final Map<K, StubIdList> newValues) {
    try {
      final MyIndex<K> index = (MyIndex<K>)getAsyncState().myIndices.get(key);
      UpdateData<K, StubIdList> updateData;

      if (MapDiffUpdateData.ourDiffUpdateEnabled) {
        updateData = new MapDiffUpdateData<K, StubIdList>(key) {
          @Override
          public void save(int inputId) throws IOException {
          }

          @Override
          protected Map<K, StubIdList> getNewValue() {
            return newValues;
          }

          @Override
          protected Map<K, StubIdList> getCurrentValue() throws IOException {
            return oldValues;
          }
        };
      }
      else {
        updateData = index.new SimpleUpdateData(key, fileId, newValues, oldValues::keySet);
      }
      index.updateWithMap(fileId, updateData);
    }
    catch (StorageException e) {
      LOG.info(e);
      requestRebuild();
    }
  }

  private static class MyIndex<K> extends MapReduceIndex<K, StubIdList, Void> {
    public MyIndex(IndexExtension<K, StubIdList, Void> extension, IndexStorage<K, StubIdList> storage) throws IOException {
      super(extension, storage);
    }

    @Override
    public void updateWithMap(final int inputId,
                              @NotNull UpdateData<K, StubIdList> updateData) throws StorageException {
      super.updateWithMap(inputId, updateData);
    }
  }

  @Override
  protected <Psi extends PsiElement> void reportStubPsiMismatch(Psi psi, VirtualFile file, Class<Psi> requiredClass) {
    if (file == null) {
      super.reportStubPsiMismatch(psi, file, requiredClass);
      return;
    }

    StringWriter writer = new StringWriter();
    //noinspection IOResourceOpenedButNotSafelyClosed
    PrintWriter out = new PrintWriter(writer);

    out.print("Invalid stub element type in index:");
    out.printf("\nfile: %s\npsiElement: %s\nrequiredClass: %s\nactualClass: %s",
               file, psi, requiredClass, psi.getClass());

    FileType fileType = file.getFileType();
    Language language = fileType instanceof LanguageFileType ?
                        LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)fileType).getLanguage(), file, psi.getProject()) :
                        Language.ANY;
    out.printf("\nvirtualFile: size:%s; stamp:%s; modCount:%s; fileType:%s; language:%s",
               file.getLength(), file.getModificationStamp(), file.getModificationCount(),
               fileType.getName(), language.getID());

    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) {
      boolean committed = PsiDocumentManager.getInstance(psi.getProject()).isCommitted(document);
      boolean saved = !FileDocumentManager.getInstance().isDocumentUnsaved(document);
      out.printf("\ndocument: size:%s; stamp:%s; committed:%s; saved:%s",
                 document.getTextLength(), document.getModificationStamp(), committed, saved);
    }

    PsiFile psiFile = psi.getManager().findFile(file);
    if (psiFile != null) {
      out.printf("\npsiFile: size:%s; stamp:%s; class:%s; language:%s",
                 psiFile.getTextLength(), psiFile.getViewProvider().getModificationStamp(), psiFile.getClass().getName(),
                 psiFile.getLanguage().getID());
    }

    StubTree stub = psiFile instanceof PsiFileWithStubSupport ? ((PsiFileWithStubSupport)psiFile).getStubTree() : null;
    FileElement treeElement = stub == null && psiFile instanceof PsiFileImpl? ((PsiFileImpl)psiFile).getTreeElement() : null;
    if (stub != null) {
      out.printf("\nstubInfo: " + stub.getDebugInfo());
    }
    else if (treeElement != null) {
      out.printf("\nfileAST: size:%s; parsed:%s", treeElement.getTextLength(), treeElement.isParsed());
    }

    out.printf("\nindexing info: " + StubUpdatingIndex.getIndexingStampInfo(file));
    LOG.error(writer.toString());
  }

  private abstract static class StubIdListContainerAction implements ValueContainer.ContainerAction<StubIdList> {
    private final IdFilter myIdFilter;

    StubIdListContainerAction(@Nullable IdFilter idFilter, @NotNull Project project) {
      myIdFilter = idFilter != null ? idFilter : ((FileBasedIndexImpl)FileBasedIndex.getInstance()).projectIndexableFiles(project);
    }

    @Override
    public boolean perform(final int id, @NotNull final StubIdList value) {
      ProgressManager.checkCanceled();
      if (myIdFilter != null && !myIdFilter.containsFileId(id)) return true;

      return process(id, value);
    }

    protected abstract boolean process(int id, StubIdList value);
  }

  private class StubIndexInitialization extends IndexInfrastructure.DataInitialization<AsyncState> {
    private final AsyncState state = new AsyncState();
    private final StringBuilder updated = new StringBuilder();
    private final StubIndexExtension<?, ?>[] myExtensions;

    public StubIndexInitialization(StubIndexExtension<?, ?>[] extensions) {
      myExtensions = extensions;
    }

    @Override
    protected void prepare() {
      boolean forceClean = Boolean.TRUE == ourForcedClean.getAndSet(Boolean.FALSE);
      for (StubIndexExtension extension : myExtensions) {
        addNestedInitializationTask(() -> {
          @SuppressWarnings("unchecked") boolean rebuildRequested = registerIndexer(extension, forceClean, state);
          if (rebuildRequested) {
            synchronized (updated) {
              updated.append(extension).append(' ');
            }
          }
        });
      }
    }

    @Override
    protected void onThrowable(Throwable t) {
      LOG.error(t);
    }

    @Override
    protected AsyncState finish() {
      if (updated.length() > 0) {
        final Throwable e = new Throwable(updated.toString());
        // avoid direct forceRebuild as it produces dependency cycle (IDEA-105485)
        ApplicationManager.getApplication().invokeLater(() -> forceRebuild(e), ModalityState.NON_MODAL);
      }
      dropUnregisteredIndices(state);
      myInitialized = true;
      return state;
    }
  }
}
