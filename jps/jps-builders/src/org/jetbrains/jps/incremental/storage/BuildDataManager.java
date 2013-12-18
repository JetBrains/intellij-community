/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.storage.BuildTargetStorages;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.incremental.IncProjectBuilder;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 22;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";
  private static final int CONCURRENCY_LEVEL = BuildRunner.PARALLEL_BUILD_ENABLED? IncProjectBuilder.MAX_BUILDER_THREADS : 1;

  private final ConcurrentMap<BuildTarget<?>, AtomicNotNullLazyValue<SourceToOutputMappingImpl>> mySourceToOutputs = 
    new ConcurrentHashMap<BuildTarget<?>, AtomicNotNullLazyValue<SourceToOutputMappingImpl>>(16, 0.75f, CONCURRENCY_LEVEL);
  private final ConcurrentMap<BuildTarget<?>, AtomicNotNullLazyValue<BuildTargetStorages>> myTargetStorages =
    new ConcurrentHashMap<BuildTarget<?>, AtomicNotNullLazyValue<BuildTargetStorages>>(16, 0.75f, CONCURRENCY_LEVEL);

  private final OneToManyPathsMapping mySrcToFormMap;
  private final Mappings myMappings;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  private final File myVersionFile;
  private StorageOwner myTargetStoragesOwner = new CompositeStorageOwner() {
    @Override
    protected Iterable<? extends StorageOwner> getChildStorages() {
      return new Iterable<StorageOwner>() {
        @Override
        public Iterator<StorageOwner> iterator() {
          final Iterator<AtomicNotNullLazyValue<BuildTargetStorages>> iterator = myTargetStorages.values().iterator();
          return new Iterator<StorageOwner>() {
            @Override
            public boolean hasNext() {
              return iterator.hasNext();
            }

            @Override
            public StorageOwner next() {
              return iterator.next().getValue();
            }

            @Override
            public void remove() {
              iterator.remove();
            }
          };
        }
      };
    }
  };


  private interface LazyValueFactory<K, V> {
    AtomicNotNullLazyValue<V> create(K key);
  }

  private LazyValueFactory<BuildTarget<?>,SourceToOutputMappingImpl> SOURCE_OUTPUT_MAPPING_VALUE_FACTORY = new LazyValueFactory<BuildTarget<?>, SourceToOutputMappingImpl>() {
    @Override
    public AtomicNotNullLazyValue<SourceToOutputMappingImpl> create(final BuildTarget<?> key) {
      return new AtomicNotNullLazyValue<SourceToOutputMappingImpl>() {
        @NotNull
        @Override
        protected SourceToOutputMappingImpl compute() {
          try {
            return new SourceToOutputMappingImpl(new File(getSourceToOutputMapRoot(key), "data"));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
    }
  };
  
  private LazyValueFactory<BuildTarget<?>,BuildTargetStorages> TARGET_STORAGES_VALUE_FACTORY = new LazyValueFactory<BuildTarget<?>, BuildTargetStorages>() {
    @Override
    public AtomicNotNullLazyValue<BuildTargetStorages> create(final BuildTarget<?> target) {
      return new AtomicNotNullLazyValue<BuildTargetStorages>() {
        @NotNull
        @Override
        protected BuildTargetStorages compute() {
          return new BuildTargetStorages(target, myDataPaths);
        }
      };
    }
  };

  public BuildDataManager(final BuildDataPaths dataPaths, BuildTargetsState targetsState, final boolean useMemoryTempCaches) throws IOException {
    myDataPaths = dataPaths;
    myTargetsState = targetsState;
    mySrcToFormMap = new OneToManyPathsMapping(new File(getSourceToFormsRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    myVersionFile = new File(myDataPaths.getDataStorageRoot(), "version.dat");
  }

  public SourceToOutputMapping getSourceToOutputMap(final BuildTarget<?> target) throws IOException {
    return fetchValue(mySourceToOutputs, target, SOURCE_OUTPUT_MAPPING_VALUE_FACTORY);
  }

  @NotNull
  public <S extends StorageOwner> S getStorage(@NotNull BuildTarget<?> target, @NotNull StorageProvider<S> provider) throws IOException {
    final BuildTargetStorages storages = fetchValue(myTargetStorages, target, TARGET_STORAGES_VALUE_FACTORY);
    return storages.getOrCreateStorage(provider);
  }

  public OneToManyPathsMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public void cleanTargetStorages(BuildTarget<?> target) throws IOException {
    try {
      AtomicNotNullLazyValue<BuildTargetStorages> storages = myTargetStorages.remove(target);
      if (storages != null) {
        storages.getValue().close();
      }
    }
    finally {
      // delete all data except src-out mapping which is cleaned in a special way
      final File[] targetData = myDataPaths.getTargetDataRoot(target).listFiles();
      if (targetData != null) {
        final File srcOutputMapRoot = getSourceToOutputMapRoot(target);
        for (File dataFile : targetData) {
          if (!FileUtil.filesEqual(dataFile, srcOutputMapRoot)) {
            FileUtil.delete(dataFile);
          }
        }
      }
    }
  }

  public void clean() throws IOException {
    try {
      myTargetStoragesOwner.close();
      myTargetStorages.clear();
    }
    finally {
      try {
        closeSourceToOutputStorages();
      }
      finally {
        try {
          wipeStorage(getSourceToFormsRoot(), mySrcToFormMap);
        }
        finally {
          final Mappings mappings = myMappings;
          if (mappings != null) {
            synchronized (mappings) {
              mappings.clean();
            }
          }
          else {
            FileUtil.delete(getMappingsRoot());
          }
        }
      }
      myTargetsState.clean();
    }
    saveVersion();
  }

  public void flush(boolean memoryCachesOnly) {
    myTargetStoragesOwner.flush(memoryCachesOnly);
    for (AtomicNotNullLazyValue<SourceToOutputMappingImpl> mapping : mySourceToOutputs.values()) {
      mapping.getValue().flush(memoryCachesOnly);
    }
    mySrcToFormMap.flush(memoryCachesOnly);
    final Mappings mappings = myMappings;
    if (mappings != null) {
      synchronized (mappings) {
        mappings.flush(memoryCachesOnly);
      }
    }
  }

  public void close() throws IOException {
    try {
      myTargetsState.save();
      try {
        myTargetStoragesOwner.close();
      }
      finally {
        myTargetStorages.clear();
      }
    }
    finally {
      try {
        closeSourceToOutputStorages();
      }
      finally {
        try {
          closeStorage(mySrcToFormMap);
        }
        finally {
          final Mappings mappings = myMappings;
          if (mappings != null) {
            try {
              mappings.close();
            }
            catch (RuntimeException e) {
              final Throwable cause = e.getCause();
              if (cause instanceof IOException) {
                throw ((IOException)cause);
              }
              throw e;
            }
          }
        }
      }
    }
  }

  public void closeSourceToOutputStorages(Collection<BuildTargetChunk> chunks) throws IOException {
    for (BuildTargetChunk chunk : chunks) {
      for (BuildTarget<?> target : chunk.getTargets()) {
        final AtomicNotNullLazyValue<SourceToOutputMappingImpl> mapping = mySourceToOutputs.remove(target);
        if (mapping != null) {
          mapping.getValue().close();
        }
      }
    }
  }

  private void closeSourceToOutputStorages() throws IOException {
    IOException ex = null;
    try {
      for (AtomicNotNullLazyValue<SourceToOutputMappingImpl> mapping : mySourceToOutputs.values()) {
        try {
          mapping.getValue().close();
        }
        catch (IOException e) {
          if (ex == null) {
            ex = e;
          }
        }
      }
    }
    finally {
      mySourceToOutputs.clear();
    }
    if (ex != null) {
      throw ex;
    }
  }

  private static <K, V> V fetchValue(ConcurrentMap<K, AtomicNotNullLazyValue<V>> container, K key, final LazyValueFactory<K, V> valueFactory) throws IOException {
    AtomicNotNullLazyValue<V> lazy = container.get(key);
    if (lazy == null) {
      final AtomicNotNullLazyValue<V> newValue = valueFactory.create(key);
      lazy = container.putIfAbsent(key, newValue);
      if (lazy == null) {
        lazy = newValue; // just initialized
      }
    }
    try {
      return lazy.getValue();
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      throw e;
    }
  }
  
  private File getSourceToOutputMapRoot(BuildTarget<?> target) {
    return new File(myDataPaths.getTargetDataRoot(target), "src-out");
  }

  private File getSourceToFormsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), SRC_TO_FORM_STORAGE);
  }

  private File getMappingsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), MAPPINGS_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  private static void wipeStorage(File root, @Nullable AbstractStateStorage<?, ?> storage) {
    if (storage != null) {
      synchronized (storage) {
        storage.wipe();
      }
    }
    else {
      FileUtil.delete(root);
    }
  }

  private static void closeStorage(@Nullable AbstractStateStorage<?, ?> storage) throws IOException {
    if (storage != null) {
      synchronized (storage) {
        storage.close();
      }
    }
  }

  private Boolean myVersionDiffers = null;

  public boolean versionDiffers() {
    final Boolean cached = myVersionDiffers;
    if (cached != null) {
      return cached;
    }
    try {
      final DataInputStream is = new DataInputStream(new FileInputStream(myVersionFile));
      try {
        final boolean diff = is.readInt() != VERSION;
        myVersionDiffers = diff;
        return diff;
      }
      finally {
        is.close();
      }
    }
    catch (FileNotFoundException ignored) {
      return false; // treat it as a new dir
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return true;
  }

  public void saveVersion() {
    final Boolean differs = myVersionDiffers;
    if (differs == null || differs) {
      try {
        FileUtil.createIfDoesntExist(myVersionFile);
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(myVersionFile));
        try {
          os.writeInt(VERSION);
          myVersionDiffers = Boolean.FALSE;
        }
        finally {
          os.close();
        }
      }
      catch (IOException ignored) {
      }
    }
  }
}
