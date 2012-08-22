package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.JpsBuilderArtifactService;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class BuildRunner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.BuildRunner");
  private final JpsModelLoader myModelLoader;
  private final Set<String> myModules;
  private BuildType myBuildType;
  private final List<String> myArtifacts;
  private final List<String> myFilePaths;
  private final Map<String, String> myBuilderParams;
  private boolean myForceCleanCaches;

  public BuildRunner(JpsModelLoader modelLoader,
                     Set<String> modules,
                     BuildType buildType,
                     List<String> artifacts, List<String> filePaths, Map<String, String> builderParams) {
    myModelLoader = modelLoader;
    myModules = modules;
    myBuildType = buildType;
    myArtifacts = artifacts;
    myFilePaths = filePaths;
    myBuilderParams = builderParams;
  }

  public ProjectDescriptor load(MessageHandler msgHandler, File dataStorageRoot, BuildFSState fsState) throws IOException {
    if (!dataStorageRoot.exists()) {
      // invoked the very first time for this project. Force full rebuild
      myBuildType = BuildType.PROJECT_REBUILD;
    }

    final boolean inMemoryMappingsDelta = System.getProperty(GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION) != null;
    ProjectTimestamps projectTimestamps = null;
    BuildDataManager dataManager = null;
    try {
      projectTimestamps = new ProjectTimestamps(dataStorageRoot);
      dataManager = new BuildDataManager(dataStorageRoot, inMemoryMappingsDelta);
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
      projectTimestamps = new ProjectTimestamps(dataStorageRoot);
      dataManager = new BuildDataManager(dataStorageRoot, inMemoryMappingsDelta);
      // second attempt succeded
      msgHandler.processMessage(new CompilerMessage("build", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
    }

    final JpsModel jpsModel = myModelLoader.loadModel();
    final Project project = myModelLoader.loadOldProject();
    return new ProjectDescriptor(project, jpsModel, fsState, projectTimestamps, dataManager, BuildLoggingManager.DEFAULT);
  }

  public void runBuild(ProjectDescriptor pd, CanceledStatus cs, @Nullable Callbacks.ConstantAffectionResolver constantSearch,
                       MessageHandler msgHandler, final boolean includeTests) throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      if (myForceCleanCaches && myModules.isEmpty() && myFilePaths.isEmpty()) {
        // if compilation scope is the whole project and cache rebuild is forced, use PROJECT_REBUILD for faster compilation
        myBuildType = BuildType.PROJECT_REBUILD;
      }

      final CompileScope compileScope = createCompilationScope(myBuildType, pd, myModules, myArtifacts, myFilePaths, includeTests);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), myBuilderParams, cs, constantSearch);
      builder.addMessageHandler(msgHandler);
      try {
        switch (myBuildType) {
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

  public BuildType getBuildType() {
    return myBuildType;
  }

  private static CompileScope createCompilationScope(BuildType buildType,
                                                     ProjectDescriptor pd,
                                                     Set<String> modules,
                                                     Collection<String> artifactNames,
                                                     Collection<String> paths, boolean includeTests) throws Exception {
    final Timestamps timestamps = pd.timestamps.getStorage();
    Set<JpsArtifact> artifacts = new HashSet<JpsArtifact>();
    for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(pd.jpsModel, false)) {
      if (artifactNames.contains(artifact.getName()) && !StringUtil.isEmpty(artifact.getOutputPath())) {
        artifacts.add(artifact);
      }
    }

    final CompileScope compileScope;
    if (buildType == BuildType.PROJECT_REBUILD || (modules.isEmpty() && paths.isEmpty())) {
      compileScope = new AllProjectScope(pd.project, pd.jpsProject, artifacts, buildType != BuildType.MAKE, includeTests);
    }
    else {
      final Set<JpsModule> forcedModules;
      if (!modules.isEmpty()) {
        forcedModules = new HashSet<JpsModule>();
        for (JpsModule m : pd.jpsProject.getModules()) {
          if (modules.contains(m.getName())) {
            forcedModules.add(m);
          }
        }
      }
      else {
        forcedModules = Collections.emptySet();
      }

      final Map<String, Set<File>> filesToCompile;
      if (!paths.isEmpty()) {
        filesToCompile = new HashMap<String, Set<File>>();
        for (String path : paths) {
          final File file = new File(path);
          final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(null, file);
          if (rd != null) {
            Set<File> files = filesToCompile.get(rd.module);
            if (files == null) {
              files = new HashSet<File>();
              filesToCompile.put(rd.module, files);
            }
            files.add(file);
            if (buildType == BuildType.FORCED_COMPILATION) {
              pd.fsState.markDirty(null, file, rd, timestamps);
            }
          }
        }
      }
      else {
        filesToCompile = Collections.emptyMap();
      }

      if (filesToCompile.isEmpty()) {
        compileScope = new ModulesScope(pd.project, pd.jpsProject, forcedModules, artifacts, buildType != BuildType.MAKE, includeTests);
      }
      else {
        compileScope = new ModulesAndFilesScope(pd.project, pd.jpsProject, forcedModules, filesToCompile, artifacts, buildType != BuildType.MAKE);
      }
    }
    return compileScope;
  }
}
