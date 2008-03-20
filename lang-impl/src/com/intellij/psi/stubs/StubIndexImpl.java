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
        final MapIndexStorage<K, Integer> storage = new MapIndexStorage<K, Integer>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), new StubIdExternalizer());
        final MemoryIndexStorage<K, Integer> memStorage = new MemoryIndexStorage<K, Integer>(storage);
        final MyIndex index = createIndex(extension, memStorage);
        myIndicies.put(name, index);
        break;
      }
      catch (IOException e) {
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
  }

  private static class StubIdExternalizer implements DataExternalizer<Integer> {
    public void save(final DataOutput out, final Integer value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return DataInputOutputUtil.readINT(in);
    }
  }

  private MyIndex createIndex(final StubIndexExtension extension, final MemoryIndexStorage memStorage) {
    return new MyIndex(memStorage);
  }

  public <Key, Psi extends PsiElement> Collection<Psi> get(final StubIndexKey<Key, Psi> indexKey, final Key key, final Project project,
                                                           final GlobalSearchScope scope) {
    try {
      final DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
      final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
      final PsiManager psiManager = PsiManager.getInstance(project);

      final List<Psi> result = new ArrayList<Psi>();
      final MyIndex<Key> index = (MyIndex<Key>)myIndicies.get(indexKey);

      final ValueContainer<Integer> container = index.getData(key);
      container.forEach(new ValueContainer.ContainerAction<Integer>() {
        public void perform(final int id, final Integer value) {
          final VirtualFile file = IndexInfrastructure.findFileById(dirIndex, fs, id);
          if (file != null && (scope == null || scope.contains(file))) {
            final PsiFileImpl psiFile = (PsiFileImpl)psiManager.findFile(file);
            if (psiFile != null) {
              StubTree stubTree = psiFile.getStubTree();
              if (stubTree == null) {
                stubTree = StubTree.readFromVFile(file, project);
                final List<StubElement<?>> plained = stubTree.getPlainList();
                final StubElement<?> stub = plained.get(value);
                final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);
                result.add((Psi)tree.getPsi());
              }
              else {
                final List<StubElement<?>> plained = stubTree.getPlainList();
                result.add((Psi)plained.get(value).getPsi());
              }
            }
          }
        }
      });

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

  public void updateIndex(StubIndexKey key,
                          int fileId,
                          Map<?, Integer> oldValues,
                          Map<?, Integer> newValues) {
    try {
      final MyIndex index = myIndicies.get(key);
      index.updateWithMap(fileId, oldValues, newValues);
    }
    catch (StorageException e) {
      throw new RuntimeException(e); // TODO!!!
    }
  }

  private static class MyIndex<K> extends MapReduceIndex<K, Integer, Void> {
    public MyIndex(final MemoryIndexStorage memStorage) {
      super(null, memStorage);
    }

    public void updateWithMap(final int inputId, final Map oldData, final Map newData) throws StorageException {
      super.updateWithMap(inputId, oldData, newData);
    }
  }

}