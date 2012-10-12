package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
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
  private static final boolean STORE_TEMP_CACHES_IN_MEMORY = PARALLEL_BUILD_ENABLED || System.getProperty(GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION) != null;
  private final JpsModelLoader myModelLoader;
  private final List<TargetTypeBuildScope> myScopes;
  private final List<String> myFilePaths;
  private final Map<String, String> myBuilderParams;
  private boolean myForceCleanCaches;

  public BuildRunner(JpsModelLoader modelLoader, List<TargetTypeBuildScope> scopes, List<String> filePaths, Map<String, String> builderParams) {
    myModelLoader = modelLoader;
    myScopes = scopes;
    myFilePaths = filePaths;
    myBuilderParams = builderParams;
  }

  public ProjectDescriptor load(MessageHandler msgHandler, File dataStorageRoot, BuildFSState fsState) throws IOException {
    final JpsModel jpsModel = myModelLoader.loadModel();
    BuildDataPaths dataPaths = new BuildDataPathsImpl(dataStorageRoot);
    BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(jpsModel);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(jpsModel);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(jpsModel);
    BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetIndex, jpsModel, index, dataPaths, ignoredFileIndex);
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
      // second attempt succeded
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
    }

    return new ProjectDescriptor(jpsModel, fsState, projectTimestamps, dataManager, BuildLoggingManager.DEFAULT, index, targetsState,
                                 targetIndex, buildRootIndex, ignoredFileIndex);
  }

  public void runBuild(ProjectDescriptor pd, CanceledStatus cs, @Nullable Callbacks.ConstantAffectionResolver constantSearch,
                       MessageHandler msgHandler, BuildType buildType) throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      if (myForceCleanCaches && myScopes.isEmpty() && myFilePaths.isEmpty()) {
        // if compilation scope is the whole project and cache rebuild is forced, use PROJECT_REBUILD for faster compilation
        buildType = BuildType.PROJECT_REBUILD;
      }

      final CompileScope compileScope = createCompilationScope(buildType, pd, myScopes, myFilePaths);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), myBuilderParams, cs, constantSearch);
      builder.addMessageHandler(msgHandler);
      try {
        switch (buildType) {
          case PROJECT_REBUILD:
            builder.build(compileScope, false, true, myForceCleanCaches);
            break;

          case FORCED_COMPILATION:
            builder.build(compileScope, false, false, myForceCleanCaches);
            break;

          case MAKE:
            builder.build(compileScope, true, false, myForceCleanCaches);
            break;

          case CLEAN:
            //todo[nik]
    //        new ProjectBuilder(new GantBinding(), project).clean();
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

  private static CompileScope createCompilationScope(BuildType buildType, ProjectDescriptor pd, List<TargetTypeBuildScope> scopes,
                                                     Collection<String> paths) throws Exception {
    Set<BuildTargetType<?>> targetTypes = new HashSet<BuildTargetType<?>>();
    Set<BuildTarget<?>> targets = new HashSet<BuildTarget<?>>();
    Map<BuildTarget<?>, Set<File>> files;

    for (TargetTypeBuildScope scope : scopes) {
      BuildTargetType<?> targetType = BuilderRegistry.getInstance().getTargetType(scope.getTypeId());
      if (targetType == null) {
        LOG.info("Unknown target type: " + scope.getTypeId());
        continue;
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
          if (buildType == BuildType.FORCED_COMPILATION) {
            pd.fsState.markDirty(null, file, descriptor, timestamps);
          }
        }
      }
    }
    else {
      files = Collections.emptyMap();
    }

    return new CompileScopeImpl(buildType != BuildType.MAKE, targetTypes, targets, files);
  }

  public List<TargetTypeBuildScope> getScopes() {
    return myScopes;
  }
}
