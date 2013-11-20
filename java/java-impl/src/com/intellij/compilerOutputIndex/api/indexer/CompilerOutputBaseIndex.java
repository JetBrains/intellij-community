package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.asm4.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.indexing.IndexInfrastructure.*;

/**
 * @author Dmitry Batkovich
 */
public abstract class CompilerOutputBaseIndex<K, V> {
  public final static ExtensionPointName<CompilerOutputBaseIndex> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.java.compilerOutputIndex");

  private final static Logger LOG = Logger.getInstance(CompilerOutputBaseIndex.class);
  private final KeyDescriptor<K> myKeyDescriptor;
  private final DataExternalizer<V> myValueExternalizer;
  protected volatile MapReduceIndex<K, V, ClassNode> myIndex;

  protected final Project myProject;

  protected volatile AtomicBoolean myInitialized = new AtomicBoolean(false);

  public CompilerOutputBaseIndex(final KeyDescriptor<K> keyDescriptor, final DataExternalizer<V> valueExternalizer, final Project project) {
    myProject = project;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
  }

  public final boolean initIfNeed() {
    if (myInitialized.compareAndSet(false, true)) {
      final MapReduceIndex<K, V, ClassNode> index;
      final Ref<Boolean> rewriteIndex = new Ref<Boolean>(false);
      try {
        final ID<K, V> indexId = getIndexId();
        if (!IndexInfrastructure.getIndexRootDir(indexId).exists()) {
          rewriteIndex.set(true);
        }
        final File storageFile = getStorageFile(indexId);
        final MapIndexStorage<K, V> indexStorage = IOUtil.openCleanOrResetBroken(
          new ThrowableComputable<MapIndexStorage<K, V>, IOException>() {
            @Override
            public MapIndexStorage<K, V> compute() throws IOException {
              return new MapIndexStorage<K, V>(storageFile, myKeyDescriptor, myValueExternalizer, 1024);
            }
          },
          new Runnable() {
            @Override
            public void run() {
              IOUtil.deleteAllFilesStartingWith(storageFile);
              rewriteIndex.set(true);
            }
          }
        );
        index = new MapReduceIndex<K, V, ClassNode>(indexId, getIndexer(), indexStorage);
        index.setInputIdToDataKeysIndex(new Factory<PersistentHashMap<Integer, Collection<K>>>() {
          @Override
          public PersistentHashMap<Integer, Collection<K>> create() {
            try {
              return IOUtil.openCleanOrResetBroken(
                new ThrowableComputable<PersistentHashMap<Integer, Collection<K>>, IOException>() {
                  @Override
                  public PersistentHashMap<Integer, Collection<K>> compute() throws IOException {
                    return FileBasedIndexImpl.createIdToDataKeysIndex(indexId, myKeyDescriptor, new MemoryIndexStorage<K, V>(indexStorage));
                  }
                },
                new Runnable() {
                  @Override
                  public void run() {
                    FileUtil.delete(getInputIndexStorageFile(getIndexId()));
                    rewriteIndex.set(true);
                  }
                }
              );
            }
            catch (IOException e) {
              throw new RuntimeException("couldn't create index", e);
            }
          }
        });
        final File versionFile = getVersionFile(indexId);
        if (versionFile.exists()) {
          if (versionDiffers(versionFile, getVersion())) {
            rewriteVersion(versionFile, getVersion());
            rewriteIndex.set(true);
            try {
              LOG.info("clearing index for updating index version");
              index.clear();
            }
            catch (StorageException e) {
              LOG.error("couldn't clear index for reinitializing", e);
              throw new RuntimeException(e);
            }
          }
        }
        else if (versionFile.createNewFile()) {
          rewriteVersion(versionFile, getVersion());
          rewriteIndex.set(true);
        }
        else {
          LOG.error(String.format("problems while access to index version file to index %s ", indexId));
        }
      }
      catch (IOException e) {
        LOG.error("couldn't initialize index", e);
        throw new RuntimeException(e);
      }
      myIndex = index;
      return rewriteIndex.get();
    }
    else {
      return false;
    }
  }

  protected abstract ID<K, V> getIndexId();

  protected abstract int getVersion();

  protected abstract DataIndexer<K, V, ClassNode> getIndexer();

  public final void closeIfInitialized() {
    if (myInitialized.get()) {
      if (myIndex != null) {
        try {
          myIndex.flush();
        }
        catch (StorageException ignored) {
        }
        myIndex.dispose();
      }
    }
  }

  public final void update(final int id, final ClassNode inputData) {
    final Boolean result = myIndex.update(id, inputData).compute();
    if (result == Boolean.FALSE) throw new RuntimeException();
  }

  public final void clearIfInitialized() {
    if (myInitialized.get()) {
      try {
        myIndex.clear();
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected final ID<K, V> generateIndexId(final String indexName) {
    return CompilerOutputIndexUtil.generateIndexId(indexName, myProject);
  }
}
