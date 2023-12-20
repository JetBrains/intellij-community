// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentHashMapValueStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.storage.BuildTargetStorages;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.dependencyView.DumbMappings;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.LoggingDependencyGraph;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * @author Eugene Zhuravlev
 */
public final class BuildDataManager {
  private static final Logger LOG = Logger.getInstance(BuildDataManager.class);

  public static final String PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY = "compiler.process.constants.non.incremental";
  private static final int VERSION = 39 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1:0);
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String SRC_TO_OUTPUT_STORAGE = "src-out";
  private static final String OUT_TARGET_STORAGE = "out-target";
  private static final String MAPPINGS_STORAGE = "mappings";
  private static final String SRC_TO_OUTPUT_FILE_NAME = "data";
  private final ConcurrentMap<BuildTarget<?>, BuildTargetStorages> myTargetStorages = new ConcurrentHashMap<>(16, 0.75f, getConcurrencyLevel());
  private final OneToManyPathsMapping mySrcToFormMap;
  private final Mappings myMappings;
  private final Object myGraphManagementLock = new Object();
  private DependencyGraph myDepGraph;
  private final NodeSourcePathMapper myDepGraphPathMapper;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  private final OutputToTargetRegistry myOutputToTargetRegistry;
  private final File myVersionFile;
  private final PathRelativizerService myRelativizer;
  private boolean myProcessConstantsIncrementally = !Boolean.parseBoolean(System.getProperty(PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, "false"));

  private final StorageProvider<SourceToOutputMappingImpl> SRC_TO_OUT_MAPPING_PROVIDER = new StorageProvider<>() {
    @Override
    public @NotNull SourceToOutputMappingImpl createStorage(File targetDataDir) throws IOException {
      return createStorage(targetDataDir, myRelativizer);
    }

    @Override
    public @NotNull SourceToOutputMappingImpl createStorage(File targetDataDir, PathRelativizerService relativizer) throws IOException {
      return new SourceToOutputMappingImpl(new File(new File(targetDataDir, SRC_TO_OUTPUT_STORAGE), SRC_TO_OUTPUT_FILE_NAME), relativizer);
    }
  };

  public BuildDataManager(BuildDataPaths dataPaths, BuildTargetsState targetsState, PathRelativizerService relativizer) throws IOException {
    myDataPaths = dataPaths;
    myTargetsState = targetsState;
    try {
      mySrcToFormMap = new OneToManyPathsMapping(new File(getSourceToFormsRoot(), "data"), relativizer);
      myOutputToTargetRegistry = new OutputToTargetRegistry(new File(getOutputToSourceRegistryRoot(), "data"), relativizer);
      File mappingsRoot = getMappingsRoot(myDataPaths.getDataStorageRoot());
      if (JavaBuilderUtil.isDepGraphEnabled()) {
        if(Boolean.parseBoolean(System.getProperty("kotlin.jps.workaround.tests", "false"))) {
          myMappings = new DumbMappings();
        }
        else {
          myMappings = null;
        }
        createDependencyGraph(mappingsRoot, false);
        LOG.info("Using DependencyGraph-based build incremental analysis");
      }
      else {
        myMappings = new Mappings(mappingsRoot, relativizer);
        myMappings.setProcessConstantsIncrementally(isProcessConstantsIncrementally());
      }
    }
    catch (IOException e) {
      try {
        close();
      }
      catch (Throwable ignored) {
      }
      throw e;
    }
    myVersionFile = new File(myDataPaths.getDataStorageRoot(), "version.dat");
    myDepGraphPathMapper = relativizer != null? new PathSourceMapper(relativizer::toFull, relativizer::toRelative) : new PathSourceMapper();
    myRelativizer = relativizer;
  }

  public void setProcessConstantsIncrementally(boolean processInc) {
    myProcessConstantsIncrementally = processInc;
    Mappings mappings = myMappings;
    if (mappings != null) {
      mappings.setProcessConstantsIncrementally(processInc);
    }
  }

  public boolean isProcessConstantsIncrementally() {
    return myProcessConstantsIncrementally;
  }

  public BuildTargetsState getTargetsState() {
    return myTargetsState;
  }

  public OutputToTargetRegistry getOutputToTargetRegistry() {
    return myOutputToTargetRegistry;
  }

  public SourceToOutputMapping getSourceToOutputMap(final BuildTarget<?> target) throws IOException {
    final SourceToOutputMappingImpl map = getStorage(target, SRC_TO_OUT_MAPPING_PROVIDER);
    return new SourceToOutputMappingWrapper(map, myTargetsState.getBuildTargetId(target));
  }

  public SourceToOutputMappingImpl createSourceToOutputMapForStaleTarget(BuildTargetType<?> targetType, String targetId) throws IOException {
    return new SourceToOutputMappingImpl(new File(getSourceToOutputMapRoot(targetType, targetId), SRC_TO_OUTPUT_FILE_NAME), myRelativizer);
  }

  public @NotNull <S extends StorageOwner> S getStorage(@NotNull BuildTarget<?> target, @NotNull StorageProvider<S> provider) throws IOException {
    final BuildTargetStorages targetStorages = myTargetStorages.computeIfAbsent(target, t -> new BuildTargetStorages(t, myDataPaths));
    return targetStorages.getOrCreateStorage(provider, myRelativizer);
  }

  public OneToManyPathsMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  @Nullable
  public GraphConfiguration getDependencyGraph() {
    synchronized (myGraphManagementLock) {
      DependencyGraph depGraph = myDepGraph;
      return depGraph == null? null : new GraphConfiguration() {
        @Override
        public @NotNull NodeSourcePathMapper getPathMapper() {
          return myDepGraphPathMapper;
        }

        @Override
        public @NotNull DependencyGraph getGraph() {
          return depGraph;
        }
      };
    }
  }

  public void cleanTargetStorages(BuildTarget<?> target) throws IOException {
    try {
      BuildTargetStorages storages = myTargetStorages.remove(target);
      if (storages != null) {
        storages.close();
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

  public void clean(Consumer<Future<?>> asyncTaskCollector) throws IOException {
    try {
      allTargetStorages(asyncTaskCollector).clean();
      myTargetStorages.clear();
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
          File mappingsRoot = getMappingsRoot(myDataPaths.getDataStorageRoot());
          final Mappings mappings = myMappings;
          if (mappings != null) {
            synchronized (mappings) {
              mappings.clean();
            }
          }
          else {
            FileUtil.delete(mappingsRoot);
          }

          if (JavaBuilderUtil.isDepGraphEnabled()) {
            createDependencyGraph(mappingsRoot, true);
          }
        }
      }
      myTargetsState.clean();
    }
    saveVersion();
  }

  public void createDependencyGraph(File mappingsRoot, boolean deleteExisting) throws IOException {
    try {
      synchronized (myGraphManagementLock) {
        DependencyGraph depGraph = myDepGraph;
        if (depGraph == null) {
          if (deleteExisting) {
            FileUtil.delete(mappingsRoot);
          }
          myDepGraph = asSynchronizedGraph(new DependencyGraphImpl(Containers.createPersistentContainerFactory(mappingsRoot.getAbsolutePath())));
        }
        else {
          try {
            depGraph.close();
          }
          finally {
            if (deleteExisting) {
              FileUtil.delete(mappingsRoot);
            }
            myDepGraph = asSynchronizedGraph(new DependencyGraphImpl(Containers.createPersistentContainerFactory(mappingsRoot.getAbsolutePath())));
          }
        }
      }
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      throw e;
    }
  }

  public void flush(boolean memoryCachesOnly) {
    allTargetStorages().flush(memoryCachesOnly);
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
        allTargetStorages().close();
      }
      finally {
        myTargetStorages.clear();
      }
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

          synchronized (myGraphManagementLock) {
            DependencyGraph depGraph = myDepGraph;
            if (depGraph != null) {
              myDepGraph = null;
              try {
                depGraph.close();
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

  public void closeSourceToOutputStorages(Collection<? extends BuildTargetChunk> chunks) throws IOException {
    IOException ex = null;
    for (BuildTargetChunk chunk : chunks) {
      for (BuildTarget<?> target : chunk.getTargets()) {
        try {
          final BuildTargetStorages targetStorages = myTargetStorages.get(target);
          if (targetStorages != null) {
            targetStorages.close(SRC_TO_OUT_MAPPING_PROVIDER);
          }
        }
        catch (IOException e) {
          LOG.info(e);
          if (ex == null) {
            ex = e;
          }
        }
      }
    }
    if (ex != null) {
      throw ex;
    }
  }

  private File getSourceToOutputMapRoot(BuildTarget<?> target) {
    return new File(myDataPaths.getTargetDataRoot(target), SRC_TO_OUTPUT_STORAGE);
  }

  private File getSourceToOutputMapRoot(BuildTargetType<?> targetType, String targetId) {
    return new File(myDataPaths.getTargetDataRoot(targetType, targetId), SRC_TO_OUTPUT_STORAGE);
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

  public PathRelativizerService getRelativizer() {
    return myRelativizer;
  }

  public static File getMappingsRoot(final File dataStorageRoot) {
    return new File(dataStorageRoot, JavaBuilderUtil.isDepGraphEnabled()? MAPPINGS_STORAGE + "-graph" : MAPPINGS_STORAGE);
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
    try (DataInputStream is = new DataInputStream(new FileInputStream(myVersionFile))) {
      final boolean diff = is.readInt() != VERSION;
      myVersionDiffers = diff;
      return diff;
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
      FileUtil.createIfDoesntExist(myVersionFile);
      try (DataOutputStream os = new DataOutputStream(new FileOutputStream(myVersionFile))) {
        os.writeInt(VERSION);
        myVersionDiffers = Boolean.FALSE;
      }
      catch (IOException ignored) {
      }
    }
  }

  public void reportUnhandledRelativizerPaths() {
    myRelativizer.reportUnhandledPaths();
  }

  public static int getConcurrencyLevel() {
    return BuildRunner.isParallelBuildEnabled() ? IncProjectBuilder.MAX_BUILDER_THREADS : 1;
  }

  private final class SourceToOutputMappingWrapper implements SourceToOutputMapping {
    private final SourceToOutputMapping myDelegate;
    private final int myBuildTargetId;

    SourceToOutputMappingWrapper(SourceToOutputMapping delegate, int buildTargetId) {
      myDelegate = delegate;
      myBuildTargetId = buildTargetId;
    }

    @Override
    public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
      try {
        myDelegate.setOutputs(srcPath, outputs);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputs, myBuildTargetId);
      }
    }

    @Override
    public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
      try {
        myDelegate.setOutput(srcPath, outputPath);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputPath, myBuildTargetId);
      }
    }

    @Override
    public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
      try {
        myDelegate.appendOutput(srcPath, outputPath);
      }
      finally {
        myOutputToTargetRegistry.addMapping(outputPath, myBuildTargetId);
      }
    }

    @Override
    public void remove(@NotNull String srcPath) throws IOException {
      myDelegate.remove(srcPath);
    }

    @Override
    public void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException {
      myDelegate.removeOutput(sourcePath, outputPath);
    }

    @Override
    public @NotNull Collection<String> getSources() throws IOException {
      return myDelegate.getSources();
    }

    @Override
    public @Nullable Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
      return myDelegate.getOutputs(srcPath);
    }

    @Override
    public @NotNull Iterator<String> getOutputsIterator(@NotNull String srcPath) throws IOException {
      return myDelegate.getOutputsIterator(srcPath);
    }

    @Override
    public @NotNull Iterator<String> getSourcesIterator() throws IOException {
      return myDelegate.getSourcesIterator();
    }
  }

  private @NotNull StorageOwner allTargetStorages() {
    return allTargetStorages(f -> {});
  }
  
  private StorageOwner allTargetStorages(Consumer<Future<?>> asyncTaskCollector) {
    return new CompositeStorageOwner() {
      @Override
      public void clean() throws IOException {
        try {
          close();
        }
        finally {
          asyncTaskCollector.accept(FileUtil.asyncDelete(myDataPaths.getTargetsDataRoot()));
        }
      }

      @Override
      protected Iterable<BuildTargetStorages> getChildStorages() {
        return () -> myTargetStorages.values().iterator();
      }
    };
  }

  private static DependencyGraph asSynchronizedGraph(DependencyGraph graph) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    DependencyGraph delegate = new LoggingDependencyGraph(graph, msg -> LOG.info(msg));
    return new DependencyGraph() {
      private final ReadWriteLock lock = new ReentrantReadWriteLock();

      @Override
      public Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources) throws IOException {
        lock.readLock().lock();
        try {
          return delegate.createDelta(sourcesToProcess, deletedSources);
        }
        finally {
          lock.readLock().unlock();
        }
      }

      @Override
      public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params) {
        lock.readLock().lock();
        try {
          return delegate.differentiate(delta, params);
        }
        finally {
          lock.readLock().unlock();
        }
      }

      @Override
      public void integrate(@NotNull DifferentiateResult diffResult) {
        lock.writeLock().lock();
        try {
          delegate.integrate(diffResult);
        }
        finally {
          lock.writeLock().unlock();
        }
      }

      @Override
      public Iterable<BackDependencyIndex> getIndices() {
        return delegate.getIndices();
      }

      @Override
      public @Nullable BackDependencyIndex getIndex(String name) {
        return delegate.getIndex(name);
      }

      @Override
      public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
        return delegate.getSources(id);
      }

      @Override
      public Iterable<ReferenceID> getRegisteredNodes() {
        return delegate.getRegisteredNodes();
      }

      @Override
      public Iterable<NodeSource> getSources() {
        return delegate.getSources();
      }

      @Override
      public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
        return delegate.getNodes(source);
      }

      @Override
      public <T extends Node<T, ?>> Iterable<T> getNodes(NodeSource src, Class<T> nodeSelector) {
        return delegate.getNodes(src, nodeSelector);
      }

      @Override
      public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
        return delegate.getDependingNodes(id);
      }

      @Override
      public void close() throws IOException {
        lock.writeLock().lock();
        try {
          delegate.close();
        }
        finally {
          lock.writeLock().unlock();
        }
      }
    };
  }

}
