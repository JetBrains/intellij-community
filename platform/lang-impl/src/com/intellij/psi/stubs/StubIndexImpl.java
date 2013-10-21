/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

@State(
  name = "FileBasedIndex",
  roamingType = RoamingType.DISABLED,
  storages = {
  @Storage(
    file = StoragePathMacros.APP_CONFIG + "/stubIndex.xml")
    }
)
public class StubIndexImpl extends StubIndex implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  private static final AtomicReference<Boolean> ourForcedClean = new AtomicReference<Boolean>(null);
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubIndexImpl");
  private final Map<StubIndexKey<?,?>, MyIndex<?>> myIndices = new THashMap<StubIndexKey<?,?>, MyIndex<?>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();

  private final StubProcessingHelper myStubProcessingHelper;

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl(FileBasedIndex fileBasedIndex /* need this to ensure initialization order*/ ) throws IOException {
    final boolean forceClean = Boolean.TRUE == ourForcedClean.getAndSet(Boolean.FALSE);

    final StubIndexExtension<?, ?>[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    boolean needRebuild = false;
    for (StubIndexExtension extension : extensions) {
      //noinspection unchecked
      needRebuild |= registerIndexer(extension, forceClean);
    }
    if (needRebuild) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        requestRebuild();
      }
      else {
        final Throwable e = new Throwable();
        // avoid direct forceRebuild as it produces dependency cycle (IDEA-105485)
        ApplicationManager.getApplication().invokeLater(
          new Runnable() {
          @Override
          public void run() {
            forceRebuild(e);
          }
        }, ModalityState.NON_MODAL
        );
      }
    }
    dropUnregisteredIndices();

    myStubProcessingHelper = new StubProcessingHelper(fileBasedIndex);
  }
  
  @Nullable
  public static StubIndexImpl getInstanceOrInvalidate() {
    if (ourForcedClean.compareAndSet(null, Boolean.TRUE)) {
      return null;
    }
    return (StubIndexImpl)getInstance();
  }

  // todo this seems to be copy-pasted from FileBasedIndex
  private <K> boolean registerIndexer(@NotNull final StubIndexExtension<K, ?> extension, final boolean forceClean) throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    myIndexIdToVersionMap.put(indexKey, version);
    final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
    final boolean versionFileExisted = versionFile.exists();
    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    boolean needRebuild = false;
    if (forceClean || IndexInfrastructure.versionDiffers(versionFile, version)) {
      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      needRebuild = !forceClean && (versionFileExisted || children != null && children.length > 0);
      if (needRebuild) {
        LOG.info("Version has changed for stub index " + extension.getKey() + ". The index will be rebuilt.");
      }
      FileUtil.delete(indexRootDir);
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, StubIdList> storage = new MapIndexStorage<K, StubIdList>(
          IndexInfrastructure.getStorageFile(indexKey),
          extension.getKeyDescriptor(),
          new StubIdExternalizer(),
          extension.getCacheSize(),
          false,
          extension instanceof StringStubIndexExtension && ((StringStubIndexExtension)extension).traceKeyHashToVirtualFileMapping()
        );

        final MemoryIndexStorage<K, StubIdList> memStorage = new MemoryIndexStorage<K, StubIdList>(storage);
        myIndices.put(indexKey, new MyIndex<K>(memStorage));
        break;
      }
      catch (IOException e) {
        LOG.info(e);
        needRebuild = true;
        FileUtil.delete(indexRootDir);
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
    return needRebuild;
  }

  private static class StubIdExternalizer implements DataExternalizer<StubIdList> {
    @Override
    public void save(final DataOutput out, @NotNull final StubIdList value) throws IOException {
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
    public StubIdList read(final DataInput in) throws IOException {
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

  @NotNull
  @Override
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull final Key key,
                                                           @NotNull final Project project,
                                                           final GlobalSearchScope scope) {
    return get(indexKey, key, project, scope, null);
  }

  @Override
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull Key key,
                                                           @NotNull Project project,
                                                           GlobalSearchScope scope,
                                                           IdFilter filter) {
    final List<Psi> result = new SmartList<Psi>();
    process(indexKey, key, project, scope, filter, new CommonProcessors.CollectProcessor<Psi>(result));
    return result;
  }

  @Override
  public <Key, Psi extends PsiElement> boolean process(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                       @NotNull Key key,
                                                       @NotNull Project project,
                                                       GlobalSearchScope scope,
                                                       @NotNull Processor<? super Psi> processor) {
    return process(indexKey, key, project, scope, null, processor);
  }

  @Override
  public <Key, Psi extends PsiElement> boolean process(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                       @NotNull final Key key,
                                                       @NotNull final Project project,
                                                       @Nullable final GlobalSearchScope scope,
                                                       @Nullable IdFilter idFilter,
                                                       @NotNull final Processor<? super Psi> processor) {
    final FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    fileBasedIndex.ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);

    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();

    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);

    try {
      try {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        FileBasedIndexImpl.disableUpToDateCheckForCurrentThread();
        index.getReadLock().lock();
        final ValueContainer<StubIdList> container = index.getData(key);

        final IdFilter finalIdFilter = idFilter != null ? idFilter : fileBasedIndex.projectIndexableFiles(project);

        return container.forEach(new ValueContainer.ContainerAction<StubIdList>() {
          @Override
          public boolean perform(final int id, @NotNull final StubIdList value) {
            ProgressManager.checkCanceled();
            if (finalIdFilter != null && !finalIdFilter.containsFileId(id)) return true;
            final VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
            if (file == null || scope != null && !scope.contains(file)) {
              return true;
            }
            return myStubProcessingHelper.processStubsInFile(project, file, value, processor);
          }

        });
      }
      finally {
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
    }

    return true;
  }

  private static void forceRebuild(@NotNull Throwable e) {
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
    processAllKeys(indexKey, project, new CommonProcessors.CollectProcessor<K>(allKeys));
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project, Processor<K> processor) {
    return processAllKeys(indexKey, processor, GlobalSearchScope.allScope(project), null);
  }

  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey, Processor<K> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter) {

    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, scope.getProject(), scope);

    final MyIndex<K> index = (MyIndex<K>)myIndices.get(indexKey);
    try {
      return index.processAllKeys(processor, idFilter);
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
    }
    return true;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    // This index must be disposed only after StubUpdatingIndex is disposed
    // To ensure this, disposing is done explicitly from StubUpdatingIndex by calling dispose() method
    // do not call this method here to avoid double-disposal
  }

  public void dispose() {
    for (UpdatableIndex index : myIndices.values()) {
      index.dispose();
    }
  }

  public void setDataBufferingEnabled(final boolean enabled) {
    for (UpdatableIndex index : myIndices.values()) {
      final IndexStorage indexStorage = ((MapReduceIndex)index).getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
  }

  public void cleanupMemoryStorage() {
    for (UpdatableIndex index : myIndices.values()) {
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
    for (UpdatableIndex index : myIndices.values()) {
      try {
        index.clear();
      }
      catch (StorageException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }

    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  @NotNull
  @Override
  public StubIndexState getState() {
    return new StubIndexState(myIndices.keySet());
  }

  @Override
  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public final Lock getWriteLock(StubIndexKey indexKey) {
    return myIndices.get(indexKey).getWriteLock();
  }

  public Collection<StubIndexKey> getAllStubIndexKeys() {
    return Collections.<StubIndexKey>unmodifiableCollection(myIndices.keySet());
  }

  public void flush(StubIndexKey key) throws StorageException {
    final MyIndex<?> index = myIndices.get(key);
    index.flush();
  }

  public <K> void updateIndex(@NotNull StubIndexKey key, int fileId, @NotNull final Map<K, StubIdList> oldValues, @NotNull Map<K, StubIdList> newValues) {
    try {
      final MyIndex<K> index = (MyIndex<K>)myIndices.get(key);
      index.updateWithMap(fileId, newValues, new Callable<Collection<K>>() {
        @Override
        public Collection<K> call() throws Exception {
          return oldValues.keySet();
        }
      });
    }
    catch (StorageException e) {
      LOG.info(e);
      requestRebuild();
    }
  }

  private static class MyIndex<K> extends MapReduceIndex<K, StubIdList, Void> {
    public MyIndex(final IndexStorage<K, StubIdList> storage) {
      super(null, null, storage);
    }

    @Override
    public void updateWithMap(final int inputId, @NotNull final Map<K, StubIdList> newData, @NotNull Callable<Collection<K>> oldKeysGetter) throws StorageException {
      super.updateWithMap(inputId, newData, oldKeysGetter);
    }
  }

  @Override
  protected <Psi extends PsiElement> void reportStubPsiMismatch(Psi psi, VirtualFile file) {
    if (file == null) {
      super.reportStubPsiMismatch(psi, file);
      return;
    }

    String msg = "Invalid stub element type in index: " + file;
    msg += "; found: " + psi;
    msg += "\nfile stamp: " + file.getModificationStamp();
    msg += "; file size: " + file.getLength();
    msg += "; file modCount: " + file.getModificationCount();

    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) {
      msg += "\nsaved: " + !FileDocumentManager.getInstance().isDocumentUnsaved(document);
      msg += "; doc stamp: " + document.getModificationStamp();
      msg += "; doc size: " + document.getTextLength();
      msg += "; committed: " + PsiDocumentManager.getInstance(psi.getProject()).isCommitted(document);
    }

    PsiFile psiFile = psi.getManager().findFile(file);
    if (psiFile != null) {
      msg += "\npsiFile size: " + psiFile.getTextLength();
      msg += "; viewProvider stamp: " + psiFile.getViewProvider().getModificationStamp();
      if (psiFile instanceof PsiFileImpl) {
        StubTree stub = ((PsiFileImpl)psiFile).getStubTree();
        if (stub == null) {
          FileElement treeElement = ((PsiFileImpl)psiFile).getTreeElement();
          msg += "; ast loaded: " + (treeElement != null);
          if (treeElement != null) {
            msg += "; ast parsed: " + treeElement.isParsed();
            msg += "; ast size: " + treeElement.getTextLength();
          }
        } else {
          msg += "\nstub info=" + stub.getDebugInfo();
        }
      }
    }

    msg += "\nindexing info: " + StubUpdatingIndex.getIndexingStampInfo(file);
    LOG.error(msg);
  }
}
