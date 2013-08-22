package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.asm4.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

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

  protected volatile MapReduceIndex<K, V, ClassReader> myIndex;

  private volatile Project myProject;

  public CompilerOutputBaseIndex(final KeyDescriptor<K> keyDescriptor, final DataExternalizer<V> valueExternalizer) {
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
  }

  public final boolean init(final Project project) {
    myProject = project;
    final MapReduceIndex<K, V, ClassReader> index;
    final Ref<Boolean> rewriteIndex = new Ref<Boolean>(false);
    try {
      final ID<K, V> indexId = getIndexId();
      if (!IndexInfrastructure.getIndexRootDir(indexId).exists()) {
        rewriteIndex.set(true);
      }
      final File storageFile = IndexInfrastructure.getStorageFile(indexId);
      final MapIndexStorage<K, V> indexStorage = new MapIndexStorage<K, V>(storageFile, myKeyDescriptor, myValueExternalizer, 1024);
      index = new MapReduceIndex<K, V, ClassReader>(indexId, getIndexer(), indexStorage);
      index.setInputIdToDataKeysIndex(new Factory<PersistentHashMap<Integer, Collection<K>>>() {
        @Override
        public PersistentHashMap<Integer, Collection<K>> create() {
          Exception failCause = null;
          for (int attempts = 0; attempts < 2; attempts++) {
            try {
              return FileBasedIndexImpl.createIdToDataKeysIndex(indexId, myKeyDescriptor, new MemoryIndexStorage<K, V>(indexStorage));
            }
            catch (IOException e) {
              failCause = e;
              FileUtil.delete(IndexInfrastructure.getInputIndexStorageFile(getIndexId()));
              rewriteIndex.set(true);
            }
          }
          throw new RuntimeException("couldn't create index", failCause);
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
            LOG.error("couldn't clear index for reinitializing");
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
      LOG.error("couldn't initialize index");
      throw new RuntimeException(e);
    }
    myIndex = index;
    return rewriteIndex.get();
  }

  protected abstract ID<K, V> getIndexId();

  protected abstract int getVersion();

  protected abstract DataIndexer<K, V, ClassReader> getIndexer();

  public final void projectClosed() {
    if (myIndex != null) {
      try {
        myIndex.flush();
      }
      catch (StorageException ignored) {
      }
      myIndex.dispose();
    }
  }

  public void update(final int id, final ClassReader classReader) {
    Boolean result = myIndex.update(id, classReader).compute();
    if (result == Boolean.FALSE) throw new RuntimeException();
  }

  public void clear() {
    try {
      myIndex.clear();
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  protected final ID<K, V> generateIndexId(final String indexName) {
    return CompilerOutputIndexUtil.generateIndexId(indexName, myProject);
  }

  protected final ID<K, V> generateIndexId(final Class aClass) {
    final String className = StringUtil.getShortName(aClass);
    return generateIndexId(StringUtil.trimEnd(className, "Index"));
  }
}
