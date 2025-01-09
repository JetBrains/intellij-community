// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetRegistryImpl;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.*;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import static org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isCompilerReferenceFSCaseSensitive;

public final class BuildRunner {
  private static final boolean USE_EXPERIMENTAL_STORAGE = Boolean.getBoolean("jps.use.experimental.storage");

  private static final Logger LOG = Logger.getInstance(BuildRunner.class);
  private final JpsModelLoader myModelLoader;
  private List<String> myFilePaths = Collections.emptyList();
  private Map<String, String> myBuilderParams = Collections.emptyMap();
  private boolean myForceCleanCaches;

  public BuildRunner(@NotNull JpsModelLoader modelLoader) {
    myModelLoader = modelLoader;
  }

  public void setFilePaths(@Nullable List<String> filePaths) {
    myFilePaths = filePaths == null ? Collections.emptyList() : filePaths;
  }

  public void setBuilderParams(@Nullable Map<String, String> builderParams) {
    myBuilderParams = builderParams == null ? Collections.emptyMap() : builderParams;
  }

  public @NotNull JpsProject loadModelAndGetJpsProject() throws IOException {
    return myModelLoader.loadModel().getProject();
  }

  /**
   * @deprecated please use {@link #load(MessageHandler, Path, BuildFSState)}
   */
  @Deprecated(forRemoval = true)
  public ProjectDescriptor load(@NotNull MessageHandler msgHandler, @NotNull File dataStorageRoot, @NotNull BuildFSState fsState) throws IOException {
    return load(msgHandler, dataStorageRoot.toPath(), fsState);
  }

  public ProjectDescriptor load(@NotNull MessageHandler msgHandler, @NotNull Path dataStorageRoot, @NotNull BuildFSState fsState) throws IOException {
    final JpsModel jpsModel = myModelLoader.loadModel();
    BuildDataPaths dataPaths = new BuildDataPathsImpl(dataStorageRoot);
    BuildTargetRegistryImpl targetRegistry = new BuildTargetRegistryImpl(jpsModel);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(jpsModel);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(jpsModel);
    BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetRegistry, jpsModel, index, dataPaths, ignoredFileIndex);
    BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(targetRegistry, buildRootIndex);

    PathRelativizerService relativizer = new PathRelativizerService(jpsModel.getProject(), isCompilerReferenceFSCaseSensitive());

    BuildDataManager dataManager = null;
    StorageManager storageManager = null;
    try {
      storageManager = createStorageManager(dataStorageRoot);
      dataManager = new BuildDataManager(dataPaths, new BuildTargetsState(new BuildTargetStateManagerImpl(dataPaths, jpsModel)), relativizer, storageManager);
      if (dataManager.versionDiffers()) {
        myForceCleanCaches = true;
        msgHandler.processMessage(new CompilerMessage(
          getRootCompilerName(), BuildMessage.Kind.INFO, JpsBuildBundle.message("build.message.dependency.data.format.has.changed.project.rebuild.required")
        ));
      }
    }
    catch (Exception e) {
      // second try
      LOG.info(e);

      if (storageManager != null) {
        storageManager.forceClose();
      }
      if (dataManager != null) {
        dataManager.close();
      }

      myForceCleanCaches = true;
      FileUtilRt.deleteRecursively(dataStorageRoot);

      dataManager = new BuildDataManager(dataPaths, new BuildTargetsState(new BuildTargetStateManagerImpl(dataPaths, jpsModel)), relativizer, createStorageManager(dataStorageRoot));
      // the second attempt succeeded
      msgHandler.processMessage(new CompilerMessage(
        getRootCompilerName(), BuildMessage.Kind.INFO, JpsBuildBundle.message("build.message.project.rebuild.forced.0", e.getMessage()))
      );
    }

    return new ProjectDescriptor(
      jpsModel, fsState, dataManager, BuildLoggingManager.DEFAULT, index, targetIndex, buildRootIndex, ignoredFileIndex
    );
  }

  private static @Nullable StorageManager createStorageManager(@NotNull Path dataStorageRoot) {
    if (USE_EXPERIMENTAL_STORAGE || ProjectStamps.PORTABLE_CACHES) {
      StorageManager manager = new StorageManager(dataStorageRoot.resolve("jps-portable-cache.db"));
      manager.open();
      return manager;
    }
    else {
      return null;
    }
  }

  public static @NotNull @Nls String getRootCompilerName() {
    return JpsBuildBundle.message("builder.name.root");
  }

  public void setForceCleanCaches(boolean forceCleanCaches) {
    myForceCleanCaches = forceCleanCaches;
  }

  public void runBuild(@NotNull ProjectDescriptor pd,
                       @NotNull CanceledStatus cs,
                       @NotNull MessageHandler msgHandler,
                       @NotNull BuildType buildType,
                       @NotNull List<TargetTypeBuildScope> scopes,
                       boolean includeDependenciesToScope) throws Exception {
    for (int attempt = 0; attempt < 2 && !cs.isCanceled(); attempt++) {
      final boolean forceClean = myForceCleanCaches && myFilePaths.isEmpty();
      final CompileScope compileScope = createCompilationScope(pd, scopes, myFilePaths, forceClean, includeDependenciesToScope);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), myBuilderParams, cs, Utils.IS_TEST_MODE);
      builder.addMessageHandler(msgHandler);
      try {
        switch (buildType) {
          case BUILD:
            builder.build(compileScope, forceClean);
            break;

          case CLEAN:
            //todo
    //        new ProjectBuilder(new GantBinding(), project).clean();
            break;
          case UP_TO_DATE_CHECK:
            builder.checkUpToDate(compileScope);
            break;
        }
        break; // break attempts loop
      }
      catch (RebuildRequestedException e) {
        if (attempt == 0) {
          LOG.info(e);
          myForceCleanCaches = true;
        }
        else {
          throw e;
        }
      }
    }
  }

  public CompileScope createCompilationScope(ProjectDescriptor pd, List<TargetTypeBuildScope> scopes) throws Exception {
    final boolean forceClean = myForceCleanCaches && myFilePaths.isEmpty();
    return createCompilationScope(pd, scopes, myFilePaths, forceClean, false);
  }

  private static CompileScope createCompilationScope(@NotNull ProjectDescriptor projectDescriptor,
                                                     @NotNull List<TargetTypeBuildScope> scopes,
                                                     @NotNull Collection<String> paths,
                                                     boolean forceClean,
                                                     boolean includeDependenciesToScope) throws Exception {
    Set<BuildTargetType<?>> targetTypes = new HashSet<>();
    Set<BuildTargetType<?>> targetTypesToForceBuild = new HashSet<>();
    Set<BuildTarget<?>> targets = new HashSet<>();
    Map<BuildTarget<?>, Set<File>> files;

    final TargetTypeRegistry typeRegistry = TargetTypeRegistry.getInstance();
    for (TargetTypeBuildScope scope : scopes) {
      final BuildTargetType<?> targetType = typeRegistry.getTargetType(scope.getTypeId());
      if (targetType == null) {
        LOG.info("Unknown target type: " + scope.getTypeId());
        continue;
      }
      if (scope.getForceBuild() || forceClean) {
        targetTypesToForceBuild.add(targetType);
      }
      if (scope.getAllTargets()) {
        targetTypes.add(targetType);
      }
      else {
        BuildTargetLoader<?> loader = targetType.createLoader(projectDescriptor.getModel());
        for (String targetId : scope.getTargetIdList()) {
          BuildTarget<?> target = loader.createTarget(targetId);
          if (target != null) {
            targets.add(target);
          }
          else {
            LOG.info("Unknown " + targetType + " target id: " + targetId);
          }
        }
      }
    }
    if (includeDependenciesToScope) {
      includeDependenciesToScope(targetTypes, targets, targetTypesToForceBuild, projectDescriptor);
    }

    if (paths.isEmpty()) {
      files = Collections.emptyMap();
    }
    else {
      boolean forceBuildAllModuleBasedTargets = false;
      for (BuildTargetType<?> type : targetTypesToForceBuild) {
        if (type instanceof JavaModuleBuildTargetType) {
          forceBuildAllModuleBasedTargets = true;
          break;
        }
      }
      files = new HashMap<>();
      for (String path : paths) {
        final File file = new File(path);
        final Collection<BuildRootDescriptor> descriptors = projectDescriptor.getBuildRootIndex().findAllParentDescriptors(file, null);
        for (BuildRootDescriptor descriptor : descriptors) {
          Set<File> fileSet = files.get(descriptor.getTarget());
          if (fileSet == null) {
            fileSet = FileCollectionFactory.createCanonicalFileSet();
            files.put(descriptor.getTarget(), fileSet);
          }
          final boolean added = fileSet.add(file);
          if (added) {
            final BuildTargetType<?> targetType = descriptor.getTarget().getTargetType();
            if (targetTypesToForceBuild.contains(targetType) || (forceBuildAllModuleBasedTargets && targetType instanceof ModuleBasedBuildTargetType)) {
              StampsStorage<?> stampStorage = projectDescriptor.dataManager.getFileStampStorage(descriptor.getTarget());
              projectDescriptor.fsState.markDirty(null, file, descriptor, stampStorage, false);
            }
          }
        }
      }
    }
    return new CompileScopeImpl(targetTypes, targetTypesToForceBuild, targets, files);
  }

  private static void includeDependenciesToScope(Set<? extends BuildTargetType<?>> targetTypes,
                                                 Set<BuildTarget<?>> targets,
                                                 Set<? super BuildTargetType<?>> targetTypesToForceBuild,
                                                 ProjectDescriptor descriptor) {
    //todo get rid of CompileContext parameter for BuildTargetIndex.getDependencies() and use it here
    TargetOutputIndex dummyIndex = new TargetOutputIndex() {
      @Override
      public Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file) {
        return List.of();
      }
    };

    List<BuildTarget<?>> current = new ArrayList<>(targets);
    while (!current.isEmpty()) {
      List<BuildTarget<?>> next = new ArrayList<>();
      for (BuildTarget<?> target : current) {
        for (BuildTarget<?> depTarget : target.computeDependencies(descriptor.getBuildTargetIndex(), dummyIndex)) {
          if (!targets.contains(depTarget) && !targetTypes.contains(depTarget.getTargetType())) {
            next.add(depTarget);
            if (targetTypesToForceBuild.contains(target.getTargetType())) {
              targetTypesToForceBuild.add(depTarget.getTargetType());
            }
          }
        }
      }
      targets.addAll(next);
      current = next;
    }
  }

  public static boolean isParallelBuildEnabled() {
    return Boolean.parseBoolean(System.getProperty(GlobalOptions.COMPILE_PARALLEL_OPTION));
  }

  public static boolean isParallelBuildAutomakeEnabled() {
    if (!isParallelBuildEnabled()) {
      return false;
    }

    String value = System.getProperty(GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION);
    return value == null || Boolean.parseBoolean(value);
  }
}
