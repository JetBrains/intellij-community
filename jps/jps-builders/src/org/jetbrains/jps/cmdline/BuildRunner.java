// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
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
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.incremental.storage.StampsStorage;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public final class BuildRunner {
  private static final Logger LOG = Logger.getInstance(BuildRunner.class);
  private final JpsModelLoader myModelLoader;
  private List<String> myFilePaths = Collections.emptyList();
  private Map<String, String> myBuilderParams = Collections.emptyMap();
  private boolean myForceCleanCaches;

  public BuildRunner(JpsModelLoader modelLoader) {
    myModelLoader = modelLoader;
  }

  public void setFilePaths(List<String> filePaths) {
    myFilePaths = filePaths != null? filePaths : Collections.emptyList();
  }

  public void setBuilderParams(Map<String, String> builderParams) {
    myBuilderParams = builderParams != null? builderParams : Collections.emptyMap();
  }

  public @NotNull JpsProject getLoadedJpsProject() throws IOException {
    return myModelLoader.loadModel().getProject();
  }

  public ProjectDescriptor load(MessageHandler msgHandler, File dataStorageRoot, BuildFSState fsState) throws IOException {
    final JpsModel jpsModel = myModelLoader.loadModel();
    BuildDataPaths dataPaths = new BuildDataPathsImpl(dataStorageRoot);
    BuildTargetRegistryImpl targetRegistry = new BuildTargetRegistryImpl(jpsModel);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(jpsModel);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(jpsModel);
    BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetRegistry, jpsModel, index, dataPaths, ignoredFileIndex);
    BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(targetRegistry, buildRootIndex);
    BuildTargetsState targetsState = new BuildTargetsState(dataPaths, jpsModel, buildRootIndex);

    PathRelativizerService relativizer = new PathRelativizerService(jpsModel.getProject());

    ProjectStamps projectStamps = null;
    BuildDataManager dataManager = null;
    try {
      projectStamps = new ProjectStamps(dataStorageRoot, targetsState, relativizer);
      dataManager = new BuildDataManager(dataPaths, targetsState, relativizer);
      if (dataManager.versionDiffers()) {
        myForceCleanCaches = true;
        msgHandler.processMessage(new CompilerMessage(getRootCompilerName(), BuildMessage.Kind.INFO,
                                                      JpsBuildBundle.message("build.message.dependency.data.format.has.changed.project.rebuild.required")));
      }
    }
    catch (Exception e) {
      // second try
      LOG.info(e);
      if (projectStamps != null) {
        projectStamps.close();
      }
      if (dataManager != null) {
        dataManager.close();
      }
      myForceCleanCaches = true;
      FileUtilRt.delete(dataStorageRoot);
      targetsState = new BuildTargetsState(dataPaths, jpsModel, buildRootIndex);
      projectStamps = new ProjectStamps(dataStorageRoot, targetsState, relativizer);
      dataManager = new BuildDataManager(dataPaths, targetsState, relativizer);
      // second attempt succeeded
      msgHandler.processMessage(new CompilerMessage(getRootCompilerName(), BuildMessage.Kind.INFO,
                                                    JpsBuildBundle.message("build.message.project.rebuild.forced.0", e.getMessage())));
    }

    return new ProjectDescriptor(jpsModel, fsState, projectStamps, dataManager, BuildLoggingManager.DEFAULT, index, targetsState,
                                 targetIndex, buildRootIndex, ignoredFileIndex);
  }

  @NotNull
  public static @Nls String getRootCompilerName() {
    return JpsBuildBundle.message("builder.name.root");
  }

  public void setForceCleanCaches(boolean forceCleanCaches) {
    myForceCleanCaches = forceCleanCaches;
  }

  /**
   * @deprecated Use {@link #runBuild(ProjectDescriptor, CanceledStatus, MessageHandler, BuildType, List, boolean)} instead.
   * constantSearch parameter is ignored
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  public void runBuild(ProjectDescriptor pd,
                       CanceledStatus cs,
                       @Nullable Callbacks.ConstantAffectionResolver constantSearch,
                       MessageHandler msgHandler,
                       BuildType buildType,
                       List<TargetTypeBuildScope> scopes, final boolean includeDependenciesToScope) throws Exception {
    runBuild(pd, cs, msgHandler, buildType, scopes, includeDependenciesToScope);
  }

  public void runBuild(ProjectDescriptor pd,
                       CanceledStatus cs,
                       MessageHandler msgHandler,
                       BuildType buildType,
                       List<TargetTypeBuildScope> scopes, final boolean includeDependenciesToScope) throws Exception {
    LOG.info("Got message with type: " + buildType);
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
            //todo[nik]
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

  private static CompileScope createCompilationScope(ProjectDescriptor pd, List<TargetTypeBuildScope> scopes, Collection<String> paths,
                                                     final boolean forceClean, final boolean includeDependenciesToScope) throws Exception {
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
        BuildTargetLoader<?> loader = targetType.createLoader(pd.getModel());
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
      includeDependenciesToScope(targetTypes, targets, targetTypesToForceBuild, pd);
    }

    final StampsStorage<? extends StampsStorage.Stamp> stampsStorage = pd.getProjectStamps().getStampStorage();
    if (!paths.isEmpty()) {
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
        final Collection<BuildRootDescriptor> descriptors = pd.getBuildRootIndex().findAllParentDescriptors(file, null);
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
              pd.fsState.markDirty(null, file, descriptor, stampsStorage, false);
            }
          }
        }
      }
    }
    else {
      files = Collections.emptyMap();
    }

    return new CompileScopeImpl(targetTypes, targetTypesToForceBuild, targets, files);
  }

  private static void includeDependenciesToScope(Set<? extends BuildTargetType<?>> targetTypes, Set<BuildTarget<?>> targets,
                                                 Set<? super BuildTargetType<?>> targetTypesToForceBuild, ProjectDescriptor descriptor) {
    //todo[nik] get rid of CompileContext parameter for BuildTargetIndex.getDependencies() and use it here
    TargetOutputIndex dummyIndex = new TargetOutputIndex() {
      @Override
      public Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file) {
        return Collections.emptyList();
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
    return SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false);
  }

  public static boolean isParallelBuildAutomakeEnabled() {
    return isParallelBuildEnabled() && SystemProperties.getBooleanProperty(GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION, true);
  }
}
