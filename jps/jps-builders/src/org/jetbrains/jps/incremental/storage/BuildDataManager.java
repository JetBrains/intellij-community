// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.tracing.Tracer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.JpsBuildBundle;
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
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public final class BuildDataManager {
  private static final Logger LOG = Logger.getInstance(BuildDataManager.class);

  public static final String PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY = "compiler.process.constants.non.incremental";

  static final String SRC_TO_OUTPUT_STORAGE = "src-out";
  private static final String MAPPINGS_STORAGE = "mappings";
  static final String SRC_TO_OUTPUT_FILE_NAME = "data";

  private final @NotNull ConcurrentMap<BuildTarget<?>, BuildTargetStorages> myTargetStorages = new ConcurrentHashMap<>();
  private final @Nullable BuildDataProvider newDataManager;
  private final @Nullable DefaultBuildDataProvider oldDataManager;
  private @Nullable ProjectStamps myFileStampService;

  private final Mappings myMappings;
  private final Object myGraphManagementLock = new Object();
  private DependencyGraph myDepGraph;
  private final NodeSourcePathMapper myDepGraphPathMapper;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState targetStateManager;
  private final BuildDataVersionManager versionManager;
  private final PathRelativizerService myRelativizer;
  private boolean myProcessConstantsIncrementally = !Boolean.parseBoolean(System.getProperty(PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, "false"));

  @ApiStatus.Internal
  @TestOnly
  public BuildDataManager(BuildDataPaths dataPaths, BuildTargetsState targetsState, @NotNull PathRelativizerService relativizer) throws IOException {
    this(dataPaths, targetsState, relativizer, null, null, null);
  }

  @ApiStatus.Internal
  public BuildDataManager(@NotNull BuildDataPaths dataPaths,
                          @NotNull BuildTargetsState targetsState,
                          @NotNull PathRelativizerService relativizer,
                          @Nullable StorageManager storageManager) throws IOException {
    this(dataPaths,
         targetsState,
         relativizer,
         storageManager == null ? null : new ExperimentalBuildDataManager(dataPaths, storageManager, relativizer),
         storageManager != null || ProjectStamps.PORTABLE_CACHES ? null : new ProjectStamps(dataPaths.getDataStorageDir(), targetsState.impl),
         null);
  }

  @SuppressWarnings("unused")
  @ApiStatus.Internal
  public static BuildDataManager createSingleDb(@NotNull BuildDataPaths dataPaths,
                                                @NotNull BuildTargetStateManager targetStateManager,
                                                @NotNull PathRelativizerService relativizer,
                                                @NotNull BuildDataVersionManager versionManager,
                                                @NotNull BuildDataProvider buildDataProvider) throws IOException {
    return new BuildDataManager(dataPaths,
                                new BuildTargetsState(targetStateManager),
                                relativizer,
                                buildDataProvider,
                                null,
                                versionManager);
  }

  private BuildDataManager(@NotNull BuildDataPaths dataPaths,
                           BuildTargetsState targetsState,
                           @NotNull PathRelativizerService relativizer,
                           @Nullable BuildDataProvider buildDataProvider,
                           @Nullable ProjectStamps projectStamps,
                           @Nullable BuildDataVersionManager versionManager) throws IOException {
    myDataPaths = dataPaths;
    targetStateManager = targetsState;
    myFileStampService = projectStamps;
    Path dataStorageRoot = dataPaths.getDataStorageDir();
    try {
      if (buildDataProvider == null) {
        newDataManager = null;
        oldDataManager = new DefaultBuildDataProvider(dataPaths, relativizer, targetStateManager);
      }
      else {
        newDataManager = buildDataProvider;
        oldDataManager = null;
      }

      Path mappingsRoot = getMappingsRoot(dataStorageRoot);
      if (JavaBuilderUtil.isDepGraphEnabled()) {
        myMappings = null;
        createDependencyGraph(mappingsRoot, false);
        // delete older mappings data if available
        FileUtilRt.deleteRecursively(getMappingsRoot(dataStorageRoot, false));
        LOG.info("Using DependencyGraph-based build incremental analysis");
      }
      else {
        myMappings = new Mappings(mappingsRoot.toFile(), relativizer);
        FileUtilRt.deleteRecursively(getMappingsRoot(dataStorageRoot, true)); // delete dep-graph data if available
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

    this.versionManager = versionManager == null ? new BuildDataVersionManagerImpl(dataStorageRoot.resolve("version.dat")) : versionManager;
    myDepGraphPathMapper = new PathSourceMapper(relativizer::toFull, relativizer::toRelative);
    myRelativizer = relativizer;
  }

  @ApiStatus.Internal
  public LibraryRoots getLibraryRoots() {
    return Objects.requireNonNullElse(newDataManager, oldDataManager).getLibraryRoots();
  }

  @ApiStatus.Internal
  // todo: method to allow using externally-created ProjectStamps; to be removed after KotlinTests for JPS are updated
  public void setFileStampService(@Nullable ProjectStamps fileStampService) {
    myFileStampService = fileStampService;
  }

  @ApiStatus.Internal
  public void clearCache() {
    if (newDataManager != null) {
      newDataManager.clearCache();
    }
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

  /**
   * @deprecated Use {@link #getTargetStateManager()} or, preferably, avoid using internal APIs.
   */
  @Deprecated
  public @NotNull BuildTargetsState getTargetsState() {
    return targetStateManager;
  }

  @ApiStatus.Internal
  public @NotNull BuildTargetStateManager getTargetStateManager() {
    return targetStateManager.impl;
  }

  public void cleanStaleTarget(@NotNull BuildTargetType<?> targetType, @NotNull String targetId) throws IOException {
    try {
      FileUtilRt.deleteRecursively(getDataPaths().getTargetDataRoot(targetType, targetId));
      Objects.requireNonNullElse(oldDataManager, newDataManager).removeStaleTarget(targetId, targetType);
    }
    finally {
      // Not everything has been migrated to custom storage, and we cannot enforce it.
      // Therefore, for safety, we provide a default implementation here.
      targetStateManager.impl.cleanStaleTarget(targetType, targetId);
    }
  }

  @ApiStatus.Internal
  public @NotNull OutputToTargetMapping getOutputToTargetMapping() {
    return Objects.requireNonNullElse(newDataManager, oldDataManager).getOutputToTargetMapping();
  }

  /**
   * @deprecated Use {@link #getOutputToTargetMapping()}
   * @return
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public @NotNull OutputToTargetRegistry getOutputToTargetRegistry() {
    return Objects.requireNonNull(oldDataManager).getOutputToTargetMapping();
  }

  public @NotNull SourceToOutputMapping getSourceToOutputMap(@NotNull BuildTarget<?> target) throws IOException {
    return Objects.requireNonNullElse(newDataManager, oldDataManager).getSourceToOutputMapping(target);
  }

  public @Nullable StampsStorage<?> getFileStampStorage(@NotNull BuildTarget<?> target) {
    if (newDataManager != null) {
      return newDataManager.getFileStampStorage(target);
    }
    return myFileStampService == null ? null : myFileStampService.getStampStorage();
  }

  /**
   * @deprecated Use {@link BuildDataManager#getFileStampStorage(BuildTarget)}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public @Nullable ProjectStamps getFileStampService() {
    return myFileStampService;
  }

  @ApiStatus.Internal
  public @NotNull SourceToOutputMappingImpl createSourceToOutputMapForStaleTarget(
    @NotNull BuildTargetType<?> targetType,
    @NotNull String targetId
  ) throws IOException {
    return new SourceToOutputMappingImpl(getSourceToOutputMapRoot(targetType, targetId).resolve(SRC_TO_OUTPUT_FILE_NAME), myRelativizer);
  }

  public @NotNull <S extends StorageOwner> S getStorage(@NotNull BuildTarget<?> target, @NotNull StorageProvider<S> provider) throws IOException {
    BuildTargetStorages targetStorages = myTargetStorages.computeIfAbsent(target, t -> new BuildTargetStorages(t, myDataPaths));
    return targetStorages.getOrCreateStorage(provider, myRelativizer);
  }

  @ApiStatus.Internal
  public @NotNull OneToManyPathMapping getSourceToFormMap(@NotNull BuildTarget<?> target) {
    return Objects.requireNonNullElse(newDataManager, oldDataManager).getSourceToForm(target);
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
        Objects.requireNonNullElse(newDataManager, oldDataManager).closeTargetMaps(target);
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

  public void clean(@NotNull Consumer<Future<?>> asyncTaskCollector) throws IOException {
    if (myFileStampService != null) {
      try {
        myFileStampService.clean();
      }
      catch (Throwable e) {
        LOG.error(new ProjectBuildException(JpsBuildBundle.message("build.message.error.cleaning.timestamps.storage"), e));
      }
    }

    try {
      allTargetStorages(asyncTaskCollector).clean();
      myTargetStorages.clear();
    }
    finally {
      try {
        Objects.requireNonNullElse(newDataManager, oldDataManager).wipeStorage();
      }
      finally {
        Path mappingsRoot = getMappingsRoot(myDataPaths.getDataStorageDir());
        Mappings mappings = myMappings;
        if (mappings != null) {
          synchronized (mappings) {
            mappings.clean();
          }
        }
        else {
          FileUtilRt.deleteRecursively(mappingsRoot);
        }

        if (JavaBuilderUtil.isDepGraphEnabled()) {
          createDependencyGraph(mappingsRoot, true);
        }
      }
      targetStateManager.impl.clean();
    }
    saveVersion();
  }

  public void createDependencyGraph(@NotNull Path mappingsRoot, boolean deleteExisting) throws IOException {
    try {
      synchronized (myGraphManagementLock) {
        DependencyGraph depGraph = myDepGraph;
        if (depGraph == null) {
          if (deleteExisting) {
            FileUtil.delete(mappingsRoot);
          }
          myDepGraph = asSynchronizedGraph(new DependencyGraphImpl(Containers.createPersistentContainerFactory(mappingsRoot.toString())));
        }
        else {
          try {
            depGraph.close();
          }
          finally {
            if (deleteExisting) {
              FileUtil.delete(mappingsRoot);
            }
            myDepGraph = asSynchronizedGraph(new DependencyGraphImpl(Containers.createPersistentContainerFactory(mappingsRoot.toString())));
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
    if (myFileStampService != null) {
      myFileStampService.flush(memoryCachesOnly);
    }

    Objects.requireNonNullElse(newDataManager, oldDataManager).flushStorage(memoryCachesOnly);

    allTargetStorages().flush(memoryCachesOnly);

    Mappings mappings = myMappings;
    if (mappings != null) {
      synchronized (mappings) {
        mappings.flush(memoryCachesOnly);
      }
    }
  }

  public void close() throws IOException {
    IOOperation.execAll(IOException.class,
      IOOperation.adapt(targetStateManager, state -> state.impl.save()),
      IOOperation.adapt(allTargetStorages(), StorageOwner::close),
      () -> myTargetStorages.clear(),
      IOOperation.adapt(Objects.requireNonNullElse(newDataManager, oldDataManager), BuildDataProvider::close),
      IOOperation.adapt(myFileStampService, StorageOwner::close),

      () -> {
        Mappings mappings = myMappings;
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
    );
  }

  private interface IOOperation<T extends Throwable> {

    void exec() throws T;

    interface Call<T, E extends Throwable> {
      void execute(T target) throws E;
    }

    static <Obj, E extends Throwable> IOOperation<E> adapt(@Nullable Obj caller, Call<Obj, E> op) {
      return () -> {
        if (caller != null) op.execute(caller);
      };
    }

    static <T extends Throwable> void execAll(Class<T> errorClass, IOOperation<T>... operations) throws T {
      execAll(errorClass, Arrays.asList(operations));
    }

    static <T extends Throwable> void execAll(Class<T> errorClass, Iterable<IOOperation<T>> operations) throws T {
      Throwable error = null;
      for (IOOperation<T> operation : operations) {
        try {
          operation.exec();
        }
        catch (Throwable e) {
          LOG.info(e);
          if (error == null) {
            error = e;
          }
        }
      }
      if (errorClass.isInstance(error)) {
        throw errorClass.cast(error);
      }
      if (error != null) {
        throw new RuntimeException(error);
      }
    }
  }

  @ApiStatus.Internal
  public void closeSourceToOutputStorages(@NotNull Collection<? extends BuildTarget<?>> targets) throws IOException {
    Tracer.Span flush = Tracer.start("closeSourceToOutputStorages");

    BuildDataProvider dataProvider = Objects.requireNonNullElse(newDataManager, oldDataManager);
    IOOperation.execAll(IOException.class, Iterators.map(targets, target -> {
      return () -> dataProvider.closeTargetMaps(target);
    }));

    flush.complete();
  }

  private @NotNull Path getSourceToOutputMapRoot(BuildTarget<?> target) {
    return myDataPaths.getTargetDataRootDir(target).resolve(SRC_TO_OUTPUT_STORAGE);
  }

  private Path getSourceToOutputMapRoot(BuildTargetType<?> targetType, String targetId) {
    return myDataPaths.getTargetDataRoot(targetType, targetId).resolve(SRC_TO_OUTPUT_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  public PathRelativizerService getRelativizer() {
    return myRelativizer;
  }

  public static @NotNull Path getMappingsRoot(@NotNull Path dataStorageRoot) {
    return getMappingsRoot(dataStorageRoot, JavaBuilderUtil.isDepGraphEnabled());
  }

  private static Path getMappingsRoot(@NotNull Path dataStorageRoot, boolean forDepGraph) {
    return dataStorageRoot.resolve(forDepGraph? MAPPINGS_STORAGE + "-graph" : MAPPINGS_STORAGE);
  }

  static void wipeStorage(@NotNull Path root, @Nullable StorageOwner storage) {
    if (storage != null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
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

  public boolean versionDiffers() {
    return versionManager.versionDiffers();
  }

  public void saveVersion() {
    versionManager.saveVersion();
  }

  public void reportUnhandledRelativizerPaths() {
    myRelativizer.reportUnhandledPaths();
  }

  private @NotNull StorageOwner allTargetStorages(@NotNull Consumer<Future<?>> asyncTaskCollector) {
    return new CompositeStorageOwner() {
      @Override
      public void clean() throws IOException {
        try {
          close();
        }
        finally {
          asyncTaskCollector.accept(FileUtil.asyncDelete(myDataPaths.getTargetsDataRoot().toFile()));
        }
      }

      @Override
      protected Iterable<? extends StorageOwner> getChildStorages() {
        Collection<BuildTargetStorages> targetStorages = myTargetStorages.values();
        return oldDataManager == null ? targetStorages : Iterators.flat(targetStorages, oldDataManager.getChildStorages());
      }
    };
  }

  private @NotNull StorageOwner allTargetStorages() {
    return allTargetStorages(f -> {});
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
