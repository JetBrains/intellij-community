/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
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

@State(
  name = "FileBasedIndex",
  storages = {
  @Storage(
    id = "stubIndex",
    file = "$APP_CONFIG$/stubIndex.xml")
    }
)
public class StubIndexImpl extends StubIndex implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  private final Map<StubIndexKey<?,?>, MyIndex<?>> myIndicies = new HashMap<StubIndexKey<?,?>, MyIndex<?>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl(FileBasedIndex fbi) throws IOException {
    final StubIndexExtension[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    for (StubIndexExtension extension : extensions) {
      registerIndexer(extension);
    }

    dropUnregisteredIndices();
  }

  private <K> void registerIndexer(final StubIndexExtension<K, ?> extension) throws IOException {
    final StubIndexKey name = extension.getKey();
    final int version = extension.getVersion();
    myIndexIdToVersionMap.put(name, version);
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    if (IndexInfrastructure.versionDiffers(versionFile, version)) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, TIntArrayList> storage = new MapIndexStorage<K, TIntArrayList>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), new StubIdExternalizer());
        final MemoryIndexStorage<K, TIntArrayList> memStorage = new MemoryIndexStorage<K, TIntArrayList>(storage);
        myIndicies.put(name, new MyIndex<K>(memStorage));
        break;
      }
      catch (IOException e) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
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

  public <Key, Psi extends PsiElement> Collection<Psi> get(final StubIndexKey<Key, Psi> indexKey, final Key key, final Project project,
                                                           final GlobalSearchScope scope) {
    try {
      final DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
      final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
      final PsiManager psiManager = PsiManager.getInstance(project);

      final List<Psi> result = new ArrayList<Psi>();
      final MyIndex<Key> index = (MyIndex<Key>)myIndicies.get(indexKey);

      index.getReadLock().lock();
      try {
        final ValueContainer<TIntArrayList> container = index.getData(key);
        container.forEach(new ValueContainer.ContainerAction<TIntArrayList>() {
          public void perform(final int id, final TIntArrayList value) {
            final VirtualFile file = IndexInfrastructure.findFileById(dirIndex, fs, id);
            if (file != null && (scope == null || scope.contains(file))) {
              final PsiFileImpl psiFile = (PsiFileImpl)psiManager.findFile(file);
              if (psiFile != null) {
                StubTree stubTree = psiFile.getStubTree();
                if (stubTree == null) {
                  stubTree = StubTree.readFromVFile(file, project);
                  final List<StubElement<?>> plained = stubTree.getPlainList();
                  for (int i = 0; i < value.size(); i++) {
                    final StubElement<?> stub = plained.get(value.get(i));
                    final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);
                    result.add((Psi)tree.getPsi());
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
      }

      return result;
    }
    catch (StorageException e) {
      throw new RuntimeException(e); // TODO!!!
    }
  }

  public <Key> Collection<Key> getAllKeys(final StubIndexKey<Key, ?> indexKey) {
    final MyIndex<Key> index = (MyIndex<Key>)myIndicies.get(indexKey);
    try {
      return index.getAllKeys();
    }
    catch (StorageException e) {
      throw new RuntimeException(e); // TODO!!!
    }
  }

  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    for (UpdatableIndex index : myIndicies.values()) {
      index.dispose();
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (ID<?, ?> key : myIndicies.keySet()) {
      indicesToDrop.remove(key.toString());
    }

    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(new ID(s)));
    }
  }

  public StubIndexState getState() {
    return new StubIndexState(myIndicies.keySet());
  }

  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public void updateIndex(StubIndexKey key, int fileId, Map<?, TIntArrayList> oldValues, Map<?, TIntArrayList> newValues) {
    try {
      final MyIndex index = myIndicies.get(key);
      index.updateWithMap(fileId, oldValues, newValues);
    }
    catch (StorageException e) {
      throw new RuntimeException(e); // TODO!!!
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