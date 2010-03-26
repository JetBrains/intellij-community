/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

@State(
  name = "FileBasedIndex",
  roamingType = RoamingType.DISABLED,
  storages = {
  @Storage(
    id = "stubIndex",
    file = "$APP_CONFIG$/stubIndex.xml")
    }
)
public class StubIndexImpl extends StubIndex implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubIndexImpl");
  private final Map<StubIndexKey<?,?>, MyIndex<?>> myIndices = new HashMap<StubIndexKey<?,?>, MyIndex<?>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl() throws IOException {
    final StubIndexExtension<?, ?>[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    boolean needRebuild = false;
    for (StubIndexExtension extension : extensions) {
      //noinspection unchecked
      needRebuild |= registerIndexer(extension);
    }
    if (needRebuild) {
      requestRebuild();
    }
    dropUnregisteredIndices();
  }

  private <K> boolean registerIndexer(final StubIndexExtension<K, ?> extension) throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    myIndexIdToVersionMap.put(indexKey, version);
    final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
    final boolean versionFileExisted = versionFile.exists();
    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    boolean needRebuild = false;
    if (IndexInfrastructure.versionDiffers(versionFile, version)) {
      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      needRebuild = versionFileExisted || children != null && children.length > 0;
      if (needRebuild) {
        LOG.info("Version has changed for stub index " + extension.getKey() + ". The index will be rebuilt.");
      }
      FileUtil.delete(indexRootDir);
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, TIntArrayList> storage = new MapIndexStorage<K, TIntArrayList>(IndexInfrastructure.getStorageFile(indexKey), extension.getKeyDescriptor(), new StubIdExternalizer(), 2 * 1024);
        final MemoryIndexStorage<K, TIntArrayList> memStorage = new MemoryIndexStorage<K, TIntArrayList>(storage);
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

  private static class StubIdExternalizer implements DataExternalizer<TIntArrayList> {
    public void save(final DataOutput out, final TIntArrayList value) throws IOException {
      int size = value.size();
      if (size == 0) {
        DataInputOutputUtil.writeSINT(out, Integer.MAX_VALUE);
      }
      else if (size == 1) {
        DataInputOutputUtil.writeSINT(out, -value.get(0));
      }
      else {
        DataInputOutputUtil.writeSINT(out, size);
        for (int i = 0; i < size; i++) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }
    }

    public TIntArrayList read(final DataInput in) throws IOException {
      int size = DataInputOutputUtil.readSINT(in);
      if (size == Integer.MAX_VALUE) {
        return new TIntArrayList();
      }
      else if (size <= 0) {
        TIntArrayList result = new TIntArrayList(1);
        result.add(-size);
        return result;
      }
      else {
        TIntArrayList result = new TIntArrayList(size);
        for (int i = 0; i < size; i++) {
          result.add(DataInputOutputUtil.readINT(in));
        }
        return result;
      }
    }
  }

  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull final StubIndexKey<Key, Psi> indexKey, @NotNull final Key key, final Project project,
                                                           final GlobalSearchScope scope) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);

    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<Psi> result = new ArrayList<Psi>();
    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);

    try {
      try {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        FileBasedIndex.disableUpToDateCheckForCurrentThread();
        index.getReadLock().lock();
        final ValueContainer<TIntArrayList> container = index.getData(key);

        container.forEach(new ValueContainer.ContainerAction<TIntArrayList>() {
          public void perform(final int id, final TIntArrayList value) {
            final VirtualFile file = IndexInfrastructure.findFileById(fs, id);
            if (file != null && (scope == null || scope.contains(file))) {
              StubTree stubTree = null;

              final PsiFile _psifile = psiManager.findFile(file);
              PsiFileWithStubSupport psiFile = null;

              if (_psifile != null && !(_psifile instanceof PsiPlainTextFile)) {
                if (_psifile instanceof PsiFileWithStubSupport) {
                  psiFile = (PsiFileWithStubSupport)_psifile;
                  stubTree = psiFile.getStubTree();
                  if (stubTree == null && psiFile instanceof PsiFileImpl) {
                    stubTree = ((PsiFileImpl)psiFile).calcStubTree();
                  }
                }
              }

              if (stubTree != null || psiFile != null) {
                if (stubTree == null) {
                  stubTree = StubTree.readFromVFile(project, file);
                  if (stubTree != null) {
                    final List<StubElement<?>> plained = stubTree.getPlainList();
                    for (int i = 0; i < value.size(); i++) {
                      final StubElement<?> stub = plained.get(value.get(i));
                      final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);

                      if (tree != null) {
                        if (tree.getElementType() == stubType(stub)) {
                          result.add((Psi)tree.getPsi());
                        }
                        else {
                          String persistedStubTree = ((PsiFileStubImpl)stubTree.getRoot()).printTree();

                          String stubTreeJustBuilt =
                              ((PsiFileStubImpl)((IStubFileElementType)((PsiFileImpl)psiFile).getContentElementType()).getBuilder()
                                  .buildStubTree(psiFile)).printTree();

                          StringBuilder builder = new StringBuilder();
                          builder.append("Oops\n");


                          builder.append("Recorded stub:-----------------------------------\n");
                          builder.append(persistedStubTree);
                          builder.append("\nAST built stub: ------------------------------------\n");
                          builder.append(stubTreeJustBuilt);
                          builder.append("\n");
                          LOG.info(builder.toString());

                          // requestReindex() may want to acquire write lock (for indices not requiring content loading)
                          // thus, because here we are under read lock, need to use invoke later
                          ApplicationManager.getApplication().invokeLater(new Runnable() {
                            public void run() {
                              FileBasedIndex.getInstance().requestReindex(file);
                            }
                          }, ModalityState.NON_MODAL);
                        }
                      }
                    }
                  }
                }
                else {
                  final List<StubElement<?>> plained = stubTree.getPlainList();
                  for (int i = 0; i < value.size(); i++) {
                    result.add((Psi)plained.get(value.get(i)).getPsi());
                  }
                }
              }
            }
          }
        });
      }
      finally {
        index.getReadLock().unlock();
        FileBasedIndex.enableUpToDateCheckForCurrentThread();
      }
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        forceRebuild(e);
      }
      else {
        throw e;
      }
    }

    return result;
  }

  private static IElementType stubType(final StubElement<?> stub) {
    if (stub instanceof PsiFileStub) {
      return ((PsiFileStub)stub).getType();
    }

    return stub.getStubType();
  }

  private static void forceRebuild(Throwable e) {
    LOG.info(e);
    requestRebuild();
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  private static void requestRebuild() {
    FileBasedIndex.requestRebuild(StubUpdatingIndex.INDEX_ID);
  }

  public <K> Collection<K> getAllKeys(final StubIndexKey<K, ?> indexKey, @NotNull Project project) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));

    final MyIndex<K> index = (MyIndex<K>)myIndices.get(indexKey);
    try {
      return index.getAllKeys();
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
    return Collections.emptyList();
  }

  @Override
  public <Key, Psi extends PsiElement> Collection<Key> getAllKeysWithValues(StubIndexKey<Key, Psi> indexKey, @NotNull Project project) {
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);

    final Set<Key> result = new THashSet<Key>();
    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);
    final Collection<Key> allKeys = getAllKeys(indexKey, project);

    for (final Key key : allKeys) {
      try {
        try {
          // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
          FileBasedIndex.disableUpToDateCheckForCurrentThread();
          index.getReadLock().lock();
          final ValueContainer<TIntArrayList> container = index.getData(key);

          container.forEach(new ValueContainer.ContainerAction<TIntArrayList>() {
            public void perform(final int id, final TIntArrayList value) {
              final VirtualFile file = IndexInfrastructure.findFileById(fs, id);
              if (file != null && (scope == null || scope.contains(file))) {
                result.add(key);
              }
            }
          });
        }
        finally {
          index.getReadLock().unlock();
          FileBasedIndex.enableUpToDateCheckForCurrentThread();
        }
      }
      catch (StorageException e) {
        forceRebuild(e);
      }
      catch (RuntimeException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof IOException || cause instanceof StorageException) {
          forceRebuild(e);
        }
        else {
          throw e;
        }
      }
    }
    return result;
  }

  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  public void initComponent() {
  }

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

  public StubIndexState getState() {
    return new StubIndexState(myIndices.keySet());
  }

  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public Lock getWriteLock(StubIndexKey indexKey) {
    return myIndices.get(indexKey).getWriteLock();
  }

  public Collection<StubIndexKey> getAllStubIndexKeys() {
    return Collections.<StubIndexKey>unmodifiableCollection(myIndices.keySet());
  }

  public void flush(StubIndexKey key) throws StorageException {
    final MyIndex<?> index = myIndices.get(key);
    index.getReadLock().lock();
    try {
      index.flush();
    }
    finally {
      index.getReadLock().unlock();
    }
  }

  public <K> void updateIndex(StubIndexKey key, int fileId, final Map<K, TIntArrayList> oldValues, Map<K, TIntArrayList> newValues) {
    try {
      final MyIndex<K> index = (MyIndex<K>)myIndices.get(key);
      index.updateWithMap(fileId, newValues, new Callable<Collection<K>>() {
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

  private static class MyIndex<K> extends MapReduceIndex<K, TIntArrayList, Void> {
    public MyIndex(final IndexStorage<K, TIntArrayList> storage) {
      super(null, null, storage);
    }

    public void updateWithMap(final int inputId, final Map<K, TIntArrayList> newData, Callable<Collection<K>> oldKeysGetter) throws StorageException {
      super.updateWithMap(inputId, newData, oldKeysGetter);
    }
  }

}
