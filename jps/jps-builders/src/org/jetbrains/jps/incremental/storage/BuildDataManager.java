// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.PersistentHashMapValueStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.storage.BuildTargetStorages;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.LoggingDependencyGraph;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Eugene Zhuravlev
 */
public final class BuildDataManager {
  private static final Logger LOG = Logger.getInstance(BuildDataManager.class);

  public static final String PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY = "compiler.process.constants.non.incremental";
  private static final int VERSION = 39 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED? 1 : 0) + (JavaBuilderUtil.isDepGraphEnabled()? 2 : 0);
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String SRC_TO_OUTPUT_STORAGE = "src-out";
  private static final String OUT_TARGET_STORAGE = "out-target";
  private static final String MAPPINGS_STORAGE = "mappings";
  private static final String SRC_TO_OUTPUT_FILE_NAME = "data";
  private final ConcurrentMap<BuildTarget<?>, BuildTargetStorages> myTargetStorages = new ConcurrentHashMap<>();
  private final OneToManyPathMapping mySrcToFormMap;
  private final Mappings myMappings;
  private final Object myGraphManagementLock = new Object();
  private DependencyGraph myDepGraph;
  private final NodeSourcePathMapper myDepGraphPathMapper;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  @Nullable private final StorageManager storageManager;
  private final OutputToTargetRegistry myOutputToTargetRegistry;
  private final File myVersionFile;
  private final PathRelativizerService myRelativizer;
  private boolean myProcessConstantsIncrementally = !Boolean.parseBoolean(System.getProperty(PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, "false"));

  private final @NotNull ConcurrentMap<BuildTarget<?>, SourceToOutputMappingWrapper> buildTargetToSourceToOutputMapping = new ConcurrentHashMap<>();

  @ApiStatus.Internal
  public BuildDataManager(BuildDataPaths dataPaths,
                          BuildTargetsState targetsState,
                          PathRelativizerService relativizer,
                          @Nullable StorageManager storageManager) throws IOException {
    myDataPaths = dataPaths;
    myTargetsState = targetsState;
    this.storageManager = storageManager;
    try {
      if (storageManager == null) {
        mySrcToFormMap = new OneToManyPathsMapping(getSourceToFormsRoot().resolve("data"), relativizer);
      }
      else {
        mySrcToFormMap = new ExperimentalOneToManyPathMapping("source-to-form", storageManager, relativizer);
      }
      myOutputToTargetRegistry = new OutputToTargetRegistry(getOutputToSourceRegistryRoot().resolve("data"), relativizer);
      File mappingsRoot = getMappingsRoot(myDataPaths.getDataStorageRoot());
      if (JavaBuilderUtil.isDepGraphEnabled()) {
        myMappings = null;
        createDependencyGraph(mappingsRoot, false);
        FileUtil.delete(getMappingsRoot(myDataPaths.getDataStorageRoot(), false)); // delete older mappings data if available
        LOG.info("Using DependencyGraph-based build incremental analysis");
      }
      else {
        myMappings = new Mappings(mappingsRoot, relativizer);
        FileUtil.delete(getMappingsRoot(myDataPaths.getDataStorageRoot(), true)); // delete dep-graph data if available
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

  public @Nullable StorageManager getStorageManager() {
    return storageManager;
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

  public void cleanStaleTarget(@NotNull BuildTargetType<?> targetType, @NotNull String targetId) throws IOException {
    try {
      FileUtilRt.deleteRecursively(getDataPaths().getTargetDataRoot(targetType, targetId));
      if (storageManager != null) {
        storageManager.removeStaleMaps(targetId, targetType.getTypeId());
      }
    }
    finally {
      getTargetsState().cleanStaleTarget(targetType, targetId);
    }
  }

  public OutputToTargetRegistry getOutputToTargetRegistry() {
    return myOutputToTargetRegistry;
  }

  public @NotNull SourceToOutputMapping getSourceToOutputMap(@NotNull BuildTarget<?> target) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(target);
    try {
      return buildTargetToSourceToOutputMapping.computeIfAbsent(target, t -> {
        SourceToOutputMapping map;
        if (storageManager == null) {
          try {
            Path file = myDataPaths.getTargetDataRootDir(t).resolve(SRC_TO_OUTPUT_STORAGE).resolve(SRC_TO_OUTPUT_FILE_NAME);
            map = new SourceToOutputMappingImpl(file, myRelativizer);
          }
          catch (IOException e) {
            LOG.info(e);
            throw new BuildDataCorruptedException(e);
          }
        }
        else {
          map = ExperimentalSourceToOutputMapping.createSourceToOutputMap(storageManager, myRelativizer, target);
        }
        return new SourceToOutputMappingWrapper(map, targetId);
      });
    }
    catch (BuildDataCorruptedException e) {
      LOG.info(e);
      throw e.getCause();
    }
  }

  @ApiStatus.Internal
  public SourceToOutputMappingImpl createSourceToOutputMapForStaleTarget(BuildTargetType<?> targetType, String targetId) throws IOException {
    return new SourceToOutputMappingImpl(getSourceToOutputMapRoot(targetType, targetId).resolve(SRC_TO_OUTPUT_FILE_NAME), myRelativizer);
  }

  public @NotNull <S extends StorageOwner> S getStorage(@NotNull BuildTarget<?> target, @NotNull StorageProvider<S> provider) throws IOException {
    final BuildTargetStorages targetStorages = myTargetStorages.computeIfAbsent(target, t -> new BuildTargetStorages(t, myDataPaths));
    return targetStorages.getOrCreateStorage(provider, myRelativizer);
  }

  @ApiStatus.Internal
  public OneToManyPathMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  @ApiStatus.Internal
  public Mappings getMappings() {
    return myMappings;
  }

  public @Nullable GraphConfiguration getDependencyGraph() {
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

  public void cleanTargetStorages(@NotNull BuildTarget<?> target) throws IOException {
    try {
      try {
        BuildTargetStorages storages = myTargetStorages.remove(target);
        if (storages != null) {
          storages.close();
        }
      }
      finally {
        SourceToOutputMappingWrapper sourceToOutput = buildTargetToSourceToOutputMapping.remove(target);
        if (sourceToOutput != null && sourceToOutput.myDelegate instanceof StorageOwner) {
          ((StorageOwner)sourceToOutput.myDelegate).close();
        }
      }
    }
    finally {
      // delete all data except src-out mapping which is cleaned specially
      List<Path> targetData = NioFiles.list(myDataPaths.getTargetDataRootDir(target));
      if (!targetData.isEmpty()) {
        Path srcOutputMapRoot = getSourceToOutputMapRoot(target);
        for (Path dataFile : targetData) {
          if (!dataFile.equals(srcOutputMapRoot)) {
            NioFiles.deleteRecursively(dataFile);
          }
        }
      }
    }
  }

  public void clean(Consumer<Future<?>> asyncTaskCollector) throws IOException {
    try {
      allTargetStorages(asyncTaskCollector).clean();
      buildTargetToSourceToOutputMapping.clear();
      myTargetStorages.clear();
      if (storageManager != null) {
        storageManager.clean();
      }
    }
    finally {
      try {
        if (mySrcToFormMap instanceof StorageOwner) {
          wipeStorage(getSourceToFormsRoot(), (StorageOwner)mySrcToFormMap);
        }
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
    if (storageManager != null) {
      storageManager.commit();
    }

    allTargetStorages().flush(memoryCachesOnly);
    myOutputToTargetRegistry.flush(memoryCachesOnly);
    if (mySrcToFormMap instanceof StorageOwner) {
      ((StorageOwner)mySrcToFormMap).flush(memoryCachesOnly);
    }

    Mappings mappings = myMappings;
    if (mappings != null) {
      synchronized (mappings) {
        mappings.flush(memoryCachesOnly);
      }
    }
  }

  public void close() throws IOException {
    try {
      if (storageManager != null) {
        try {
          storageManager.close();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }

      myTargetsState.save();
      try {
        allTargetStorages().close();
      }
      finally {
        myTargetStorages.clear();
        buildTargetToSourceToOutputMapping.clear();
      }
    }
    finally {
      try {
        myOutputToTargetRegistry.close();
      }
      finally {
        try {
          if (mySrcToFormMap instanceof StorageOwner) {
            synchronized (mySrcToFormMap) {
              ((StorageOwner)mySrcToFormMap).close();
            }
          }
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

  public void closeSourceToOutputStorages(@NotNull Collection<BuildTargetChunk> chunks) throws IOException {
    IOException ex = null;
    for (BuildTargetChunk chunk : chunks) {
      for (BuildTarget<?> target : chunk.getTargets()) {
        try {
          SourceToOutputMappingWrapper sourceToOutputMapping = buildTargetToSourceToOutputMapping.remove(target);
          if (sourceToOutputMapping != null) {
            (((StorageOwner)sourceToOutputMapping.myDelegate)).close();
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

  private @NotNull Path getSourceToOutputMapRoot(BuildTarget<?> target) {
    return myDataPaths.getTargetDataRootDir(target).resolve(SRC_TO_OUTPUT_STORAGE);
  }

  private Path getSourceToOutputMapRoot(BuildTargetType<?> targetType, String targetId) {
    return myDataPaths.getTargetDataRoot(targetType, targetId).resolve(SRC_TO_OUTPUT_STORAGE);
  }

  private @NotNull Path getSourceToFormsRoot() {
    return myDataPaths.getDataStorageRoot().toPath().resolve(SRC_TO_FORM_STORAGE);
  }

  private @NotNull Path getOutputToSourceRegistryRoot() {
    return myDataPaths.getDataStorageRoot().toPath().resolve(OUT_TARGET_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  public PathRelativizerService getRelativizer() {
    return myRelativizer;
  }

  public static File getMappingsRoot(final File dataStorageRoot) {
    return getMappingsRoot(dataStorageRoot, JavaBuilderUtil.isDepGraphEnabled());
  }

  private static File getMappingsRoot(final File dataStorageRoot, boolean forDepGraph) {
    return new File(dataStorageRoot, forDepGraph? MAPPINGS_STORAGE + "-graph" : MAPPINGS_STORAGE);
  }

  private static void wipeStorage(@NotNull Path root, @Nullable StorageOwner storage) {
    if (storage != null) {
      synchronized (storage) {
        try {
          storage.clean();
        }
        catch (IOException ignore) {
        }
      }
    }
    else {
      try {
        FileUtilRt.deleteRecursively(root);
      }
      catch (IOException ignore) {
      }
      catch (Exception e) {
        LOG.warn(e);
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

  private final class SourceToOutputMappingWrapper implements SourceToOutputMapping, Supplier<@Nullable StorageOwner> {
    private final SourceToOutputMapping myDelegate;
    private final int myBuildTargetId;

    SourceToOutputMappingWrapper(SourceToOutputMapping delegate, int buildTargetId) {
      myDelegate = delegate;
      myBuildTargetId = buildTargetId;
    }

    @Override
    public @Nullable StorageOwner get() {
      return myDelegate instanceof StorageOwner? (StorageOwner)myDelegate : null;
    }

    @Override
    public void setOutputs(@NotNull String srcPath, @NotNull List<String> outputs) throws IOException {
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
    public @Nullable Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
      return myDelegate.getOutputs(srcPath);
    }

    @Override
    public @NotNull Iterator<String> getSourcesIterator() throws IOException {
      return myDelegate.getSourcesIterator();
    }

    @Override
    public @NotNull SourceToOutputMappingCursor cursor() throws IOException {
      return myDelegate.cursor();
    }
  }

  private @NotNull StorageOwner allTargetStorages() {
    return allTargetStorages(f -> {});
  }
  
  private @NotNull StorageOwner allTargetStorages(@NotNull Consumer<Future<?>> asyncTaskCollector) {
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
      protected Iterable<? extends StorageOwner> getChildStorages() {
        return () -> {
          return Stream.concat(
            myTargetStorages.values().stream(),
            buildTargetToSourceToOutputMapping.values().stream()
              .map(wrapper -> {
                SourceToOutputMapping delegate = wrapper.myDelegate;
                return delegate instanceof StorageOwner ? (StorageOwner)delegate : null;
              })
              .filter(o -> o != null)
          ).iterator();
        };
      }
    };
  }

  private static DependencyGraph asSynchronizedGraph(DependencyGraph graph) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    DependencyGraph delegate = new LoggingDependencyGraph(graph, msg -> LOG.info(msg));
    return new DependencyGraph() {
      private final ReadWriteLock lock = new ReentrantReadWriteLock();

      @Override
      public Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources, boolean isSourceOnly) throws IOException {
        lock.readLock().lock();
        try {
          return delegate.createDelta(sourcesToProcess, deletedSources, isSourceOnly);
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
