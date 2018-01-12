/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.io.PersistentHashMapValueStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.storage.BuildTargetStorages;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
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
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 36 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1:0);
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String OUT_TARGET_STORAGE = "out-target";
  private static final String MAPPINGS_STORAGE = "mappings";
  private static final int CONCURRENCY_LEVEL = BuildRunner.PARALLEL_BUILD_ENABLED? IncProjectBuilder.MAX_BUILDER_THREADS : 1;

  private final ConcurrentMap<BuildTarget<?>, AtomicNotNullLazyValue<SourceToOutputMappingImpl>> mySourceToOutputs =
    new ConcurrentHashMap<>(16, 0.75f, CONCURRENCY_LEVEL);
  private final ConcurrentMap<BuildTarget<?>, AtomicNotNullLazyValue<BuildTargetStorages>> myTargetStorages =
    new ConcurrentHashMap<>(16, 0.75f, CONCURRENCY_LEVEL);

  private final OneToManyPathsMapping mySrcToFormMap;
  private final Mappings myMappings;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  private final OutputToTargetRegistry myOutputToTargetRegistry;
  private final File myVersionFile;
  private final StorageOwner myTargetStoragesOwner = new CompositeStorageOwner() {
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
            throw new BuildDataCorruptedException(e);
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
    myOutputToTargetRegistry = new OutputToTargetRegistry(new File(getOutputToSourceRegistryRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(myDataPaths.getDataStorageRoot()), useMemoryTempCaches);
    myVersionFile = new File(myDataPaths.getDataStorageRoot(), "version.dat");
  }

  public BuildTargetsState getTargetsState() {
    return myTargetsState;
  }

  public OutputToTargetRegistry getOutputToTargetRegistry() {
    return myOutputToTargetRegistry;
  }

  public SourceToOutputMapping getSourceToOutputMap(final BuildTarget<?> target) throws IOException {
    final SourceToOutputMappingImpl sourceToOutputMapping = fetchValue(mySourceToOutputs, target, SOURCE_OUTPUT_MAPPING_VALUE_FACTORY);
    final int buildTargetId = myTargetsState.getBuildTargetId(target);
    return new SourceToOutputMappingWrapper(sourceToOutputMapping, buildTargetId);
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
      myTargetStoragesOwner.clean();
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
          try {
            wipeStorage(getOutputToSourceRegistryRoot(), myOutputToTargetRegistry);
          }
          finally {
            final Mappings mappings = myMappings;
            if (mappings != null) {
              synchronized (mappings) {
                mappings.clean();
              }
            }
            else {
              FileUtil.delete(getMappingsRoot(myDataPaths.getDataStorageRoot()));
            }
            
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
    myOutputToTargetRegistry.flush(memoryCachesOnly);
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
          myOutputToTargetRegistry.close();
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
              catch (BuildDataCorruptedException e) {
                throw e.getCause();
              }
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
      for (AtomicNotNullLazyValue<SourceToOutputMappingImpl> lazy : mySourceToOutputs.values()) {
        try {
          final SourceToOutputMappingImpl mapping = lazy.getValue();
          try {
            mapping.close();
          }
          catch (IOException e) {
            if (ex == null) {
              ex = e;
            }
          }
        }
        catch (Throwable ignored) {
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
    catch (BuildDataCorruptedException e) {
      throw e.getCause();
    }
  }
  
  private File getSourceToOutputMapRoot(BuildTarget<?> target) {
    return new File(myDataPaths.getTargetDataRoot(target), "src-out");
  }

  private File getSourceToFormsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), SRC_TO_FORM_STORAGE);
  }

  private File getOutputToSourceRegistryRoot() {
    return new File(myDataPaths.getDataStorageRoot(), OUT_TARGET_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  public static File getMappingsRoot(final File dataStorageRoot) {
    return new File(dataStorageRoot, MAPPINGS_STORAGE);
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
  
  private final class SourceToOutputMappingWrapper implements SourceToOutputMapping {
    private final SourceToOutputMapping myDelegate;
    private final int myBuildTargetId;

    SourceToOutputMappingWrapper(SourceToOutputMapping delegate, int buildTargetId) {
      myDelegate = delegate;
      myBuildTargetId = buildTargetId;
    }

    public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
      try {
        myDelegate.setOutputs(srcPath, outputs);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputs, myBuildTargetId);
      }
    }

    public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
      try {
        myDelegate.setOutput(srcPath, outputPath);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputPath, myBuildTargetId);
      }
    }

    public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
      try {
        myDelegate.appendOutput(srcPath, outputPath);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputPath, myBuildTargetId);
      }
    }

    public void remove(@NotNull String srcPath) throws IOException {
      myDelegate.remove(srcPath);
    }

    public void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException {
      myDelegate.removeOutput(sourcePath, outputPath);
    }

    @NotNull 
    public Collection<String> getSources() throws IOException {
      return myDelegate.getSources();
    }

    @Nullable 
    public Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
      return myDelegate.getOutputs(srcPath);
    }

    @NotNull 
    public Iterator<String> getSourcesIterator() throws IOException {
      return myDelegate.getSourcesIterator();
    }
  }
}
