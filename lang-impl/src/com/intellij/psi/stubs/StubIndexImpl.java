/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

  public static final int OK = 1;
  public static final int NEED_REBUILD = 2;
  public static final int REBUILD_IN_PROGRESS = 3;
  private final AtomicInteger myRebuildStatus = new AtomicInteger(OK);

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl() throws IOException {
    final StubIndexExtension<?, ?>[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    boolean needRebuild = false;
    for (StubIndexExtension extension : extensions) {
      //noinspection unchecked
      needRebuild |= registerIndexer(extension);
    }
    if (needRebuild) {
      myRebuildStatus.set(NEED_REBUILD);
    }
    dropUnregisteredIndices();
  }

  private <K> boolean registerIndexer(final StubIndexExtension<K, ?> extension) throws IOException {
    boolean needRebuild = false;
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    myIndexIdToVersionMap.put(indexKey, version);
    final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
    final boolean versionFileExisted = versionFile.exists();
    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    if (IndexInfrastructure.versionDiffers(versionFile, version)) {
      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      needRebuild = versionFileExisted || (children != null && children.length > 0);
      FileUtil.delete(indexRootDir);
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, TIntArrayList> storage = new MapIndexStorage<K, TIntArrayList>(IndexInfrastructure.getStorageFile(indexKey), extension.getKeyDescriptor(), new StubIdExternalizer(), 16 * 1024);
        final MemoryIndexStorage<K, TIntArrayList> memStorage = new MemoryIndexStorage<K, TIntArrayList>(storage);
        myIndices.put(indexKey, new MyIndex<K>(memStorage));
        break;
      }
      catch (IOException e) {
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
      if (size == 1) {
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
      if (size < 0) {
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

  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull final StubIndexKey<Key, Psi> indexKey, final @NotNull Key key, final Project project,
                                                           final GlobalSearchScope scope) {
    checkRebuild();

    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID);

    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<Psi> result = new ArrayList<Psi>();
    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);

    final Lock lock = index.getReadLock();
    lock.lock();
    try {
      final ValueContainer<TIntArrayList> container = index.getData(key);
      container.forEach(new ValueContainer.ContainerAction<TIntArrayList>() {
        public void perform(final int id, final TIntArrayList value) {
          final VirtualFile file = IndexInfrastructure.findFileById(fs, id);
          if (file != null && (scope == null || scope.contains(file))) {
            final PsiFileWithStubSupport psiFile = (PsiFileWithStubSupport)psiManager.findFile(file);
            if (psiFile != null) {
              StubTree stubTree = psiFile.getStubTree();
              if (stubTree == null) {
                stubTree = StubTree.readFromVFile(file, project);
                if (stubTree != null) {
                  final List<StubElement<?>> plained = stubTree.getPlainList();
                  for (int i = 0; i < value.size(); i++) {
                    final StubElement<?> stub = plained.get(value.get(i));
                    final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);

                    if (tree != null) {
                      if (tree.getElementType() == stub.getStubType()) {
                        result.add((Psi)tree.getPsi());
                      }
                      else {
                        System.out.println("Oops");
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
    finally {
      lock.unlock();
    }

    return result;
  }

  private void forceRebuild(Throwable e) {
    LOG.info(e);
    myRebuildStatus.set(NEED_REBUILD);
    checkRebuild();
  }

  private void checkRebuild() {
    if (myRebuildStatus.compareAndSet(NEED_REBUILD, REBUILD_IN_PROGRESS)) {
      StubUpdatingIndex.scheduleStubIndicesRebuild(new Runnable() {
        public void run() {
          myRebuildStatus.compareAndSet(REBUILD_IN_PROGRESS, OK);
        }
      });
    }
    if (myRebuildStatus.get() == REBUILD_IN_PROGRESS) {
      throw new ProcessCanceledException();
    }
  }

  public <Key> Collection<Key> getAllKeys(final StubIndexKey<Key, ?> indexKey) {
    checkRebuild();
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID);

    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);
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

  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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

  public void clearIndex(StubIndexKey<?, ?> indexKey) {
    try {
      myIndices.get(indexKey).clear();
    }
    catch (StorageException e) {
      LOG.error(e);
      throw new RuntimeException(e);
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

  public StubIndexState getState() {
    return new StubIndexState(myIndices.keySet());
  }

  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public void updateIndex(StubIndexKey key, int fileId, Map<?, TIntArrayList> oldValues, Map<?, TIntArrayList> newValues) {
    try {
      final MyIndex index = myIndices.get(key);
      index.updateWithMap(fileId, oldValues, newValues);
    }
    catch (StorageException e) {
      LOG.info(e);
      myRebuildStatus.set(NEED_REBUILD);
    }
  }

  private static class MyIndex<K> extends MapReduceIndex<K, TIntArrayList, Void> {
    public MyIndex(final IndexStorage<K, TIntArrayList> storage) {
      super(null, storage);
    }

    public void updateWithMap(final int inputId, final Map<K, TIntArrayList> oldData, final Map<K, TIntArrayList> newData) throws StorageException {
      super.updateWithMap(inputId, oldData, newData);
    }
  }

}