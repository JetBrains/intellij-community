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
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
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
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author nik
 */
public class BuildRunner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildRunner");
  public static final boolean PARALLEL_BUILD_ENABLED = Boolean.parseBoolean(System.getProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "false"));
  private static final boolean STORE_TEMP_CACHES_IN_MEMORY = PARALLEL_BUILD_ENABLED || Boolean.valueOf(System.getProperty(GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION, "true"));
  private final JpsModelLoader myModelLoader;
  private List<String> myFilePaths = Collections.emptyList();
  private Map<String, String> myBuilderParams = Collections.emptyMap();
  private boolean myForceCleanCaches;

  public BuildRunner(JpsModelLoader modelLoader) {
    myModelLoader = modelLoader;
  }

  public void setFilePaths(List<String> filePaths) {
    myFilePaths = filePaths != null? filePaths : Collections.<String>emptyList();
  }

  public void setBuilderParams(Map<String, String> builderParams) {
    myBuilderParams = builderParams != null? builderParams : Collections.<String, String>emptyMap();
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

    ProjectTimestamps projectTimestamps = null;
    BuildDataManager dataManager = null;
    try {
      projectTimestamps = new ProjectTimestamps(dataStorageRoot, targetsState);
      dataManager = new BuildDataManager(dataPaths, targetsState, STORE_TEMP_CACHES_IN_MEMORY);
      if (dataManager.versionDiffers()) {
        myForceCleanCaches = true;
        msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Dependency data format has changed, project rebuild required"));
      }
    }
    catch (Exception e) {
      // second try
      LOG.info(e);
      if (projectTimestamps != null) {
        projectTimestamps.close();
      }
      if (dataManager != null) {
        dataManager.close();
      }
      myForceCleanCaches = true;
      FileUtil.delete(dataStorageRoot);
      targetsState = new BuildTargetsState(dataPaths, jpsModel, buildRootIndex);
      projectTimestamps = new ProjectTimestamps(dataStorageRoot, targetsState);
      dataManager = new BuildDataManager(dataPaths, targetsState, STORE_TEMP_CACHES_IN_MEMORY);
      // second attempt succeeded
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
    }

    return new ProjectDescriptor(jpsModel, fsState, projectTimestamps, dataManager, BuildLoggingManager.DEFAULT, index, targetsState,
                                 targetIndex, buildRootIndex, ignoredFileIndex);
  }

  public void setForceCleanCaches(boolean forceCleanCaches) {
    myForceCleanCaches = forceCleanCaches;
  }

  public void runBuild(ProjectDescriptor pd,
                       CanceledStatus cs,
                       @Nullable Callbacks.ConstantAffectionResolver constantSearch,
                       MessageHandler msgHandler,
                       BuildType buildType,
                       List<TargetTypeBuildScope> scopes, final boolean includeDependenciesToScope) throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      final boolean forceClean = myForceCleanCaches && myFilePaths.isEmpty();
      final CompileScope compileScope = createCompilationScope(pd, scopes, myFilePaths, forceClean, includeDependenciesToScope);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), myBuilderParams, cs, constantSearch, Utils.IS_TEST_MODE);
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

  private static CompileScope createCompilationScope(ProjectDescriptor pd, List<TargetTypeBuildScope> scopes, Collection<String> paths,
                                                     final boolean forceClean, final boolean includeDependenciesToScope) throws Exception {
    Set<BuildTargetType<?>> targetTypes = new HashSet<BuildTargetType<?>>();
    Set<BuildTargetType<?>> targetTypesToForceBuild = new HashSet<BuildTargetType<?>>();
    Set<BuildTarget<?>> targets = new HashSet<BuildTarget<?>>();
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

    final Timestamps timestamps = pd.timestamps.getStorage();
    if (!paths.isEmpty()) {
      files = new HashMap<BuildTarget<?>, Set<File>>();
      for (String path : paths) {
        final File file = new File(path);
        final Collection<BuildRootDescriptor> descriptors = pd.getBuildRootIndex().findAllParentDescriptors(file, null);
        for (BuildRootDescriptor descriptor : descriptors) {
          Set<File> fileSet = files.get(descriptor.getTarget());
          if (fileSet == null) {
            fileSet = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
            files.put(descriptor.getTarget(), fileSet);
          }
          fileSet.add(file);
          if (targetTypesToForceBuild.contains(descriptor.getTarget().getTargetType())) {
            pd.fsState.markDirty(null, file, descriptor, timestamps, false);
          }
        }
      }
    }
    else {
      files = Collections.emptyMap();
    }

    return new CompileScopeImpl(targetTypes, targetTypesToForceBuild, targets, files);
  }

  private static void includeDependenciesToScope(Set<BuildTargetType<?>> targetTypes, Set<BuildTarget<?>> targets,
                                                 Set<BuildTargetType<?>> targetTypesToForceBuild, ProjectDescriptor descriptor) {
    //todo[nik] get rid of CompileContext parameter for BuildTargetIndex.getDependencies() and use it here
    TargetOutputIndex dummyIndex = new TargetOutputIndex() {
      @Override
      public Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file) {
        return Collections.emptyList();
      }
    };

    List<BuildTarget<?>> current = new ArrayList<BuildTarget<?>>(targets);
    while (!current.isEmpty()) {
      List<BuildTarget<?>> next = new ArrayList<BuildTarget<?>>();
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
}
