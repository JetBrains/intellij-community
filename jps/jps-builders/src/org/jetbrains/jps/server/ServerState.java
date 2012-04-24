package org.jetbrains.jps.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import groovy.util.Node;
import groovy.util.XmlParser;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.GlobalLibrary;
import org.jetbrains.jps.api.SdkLibrary;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.idea.SystemOutErrorReporter;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
class ServerState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.server.ServerState");
  public static final String IDEA_PROJECT_DIRNAME = ".idea";

  private final Map<String, ProjectDescriptor> myProjects = new HashMap<String, ProjectDescriptor>();

  private final Object myConfigurationLock = new Object();
  private final Map<String, String> myPathVariables = new HashMap<String, String>();
  private final List<GlobalLibrary> myGlobalLibraries = new ArrayList<GlobalLibrary>();
  private volatile String myGlobalEncoding = null;
  private volatile boolean myKeepTempCachesInMemory = false;
  private volatile String myIgnoredFilesPatterns;

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars, String globalEncoding, String ignoredFilesPatterns) {
    synchronized (myConfigurationLock) {
      clearCahedState();
      myGlobalLibraries.addAll(libs);
      myPathVariables.putAll(pathVars);
      myGlobalEncoding = StringUtil.isEmpty(globalEncoding)? null : globalEncoding;
      myIgnoredFilesPatterns = StringUtil.isEmpty(ignoredFilesPatterns)? "" : ignoredFilesPatterns;
    }
  }

  public final void clearCahedState() {
    synchronized (myConfigurationLock) {
      for (Map.Entry<String, ProjectDescriptor> entry : myProjects.entrySet()) {
        final String projectPath = entry.getKey();
        final ProjectDescriptor descriptor = entry.getValue();
        descriptor.release();
      }
      myProjects.clear(); // projects should be reloaded against the latest data
      myGlobalLibraries.clear();
      myPathVariables.clear();
    }
  }

  public boolean isKeepTempCachesInMemory() {
    return myKeepTempCachesInMemory;
  }

  public void setKeepTempCachesInMemory(boolean keepTempCachesInMemory) {
    myKeepTempCachesInMemory = keepTempCachesInMemory;
  }

  public void notifyFileChanged(ProjectDescriptor pd, File file) {
    try {
      final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(file);
      if (rd != null) {
        pd.fsState.markDirty(file, rd, pd.timestamps.getStorage());
      }
    }
    catch (Exception e) {
      LOG.error(e); // todo
    }
  }

  public void notifyFileDeleted(final ProjectDescriptor pd, File file) {
    try {
      final RootDescriptor moduleAndRoot = pd.rootsIndex.getModuleAndRoot(file);
      if (moduleAndRoot != null) {
        pd.fsState.registerDeleted(moduleAndRoot.module, file, moduleAndRoot.isTestRoot, pd.timestamps.getStorage());
      }
    }
    catch (Exception e) {
      LOG.error(e); // todo
    }
  }

  @Nullable
  public ProjectDescriptor getProjectDescriptor(String projectPath) {
    final ProjectDescriptor pd;
    synchronized (myConfigurationLock) {
      pd = myProjects.get(projectPath);
      if (pd != null) {
        pd.incUsageCounter();
      }
    }
    return pd;
  }

  public void clearProjectCache(Collection<String> projectPaths) {
    synchronized (myConfigurationLock) {
      for (String projectPath : projectPaths) {
        final ProjectDescriptor descriptor = myProjects.remove(projectPath);
        if (descriptor != null) {
          descriptor.release();
        }
      }
    }
  }

  public void startBuild(String projectPath, BuildType buildType, Set<String> modules, Collection<String> artifacts,
                         Map<String, String> builderParams, Collection<String> paths, final MessageHandler msgHandler, CanceledStatus cs) throws Throwable{

    boolean forceCleanCaches = false;
    ProjectDescriptor pd;
    synchronized (myConfigurationLock) {
      pd = myProjects.get(projectPath);
      if (pd == null) {
        final Project project = loadProject(projectPath);
        final BuildFSState fsState = new BuildFSState(false);
        ProjectTimestamps timestamps = null;
        BuildDataManager dataManager = null;
        final File dataStorageRoot = Utils.getDataStorageRoot(project);
        try {
          timestamps = new ProjectTimestamps(dataStorageRoot);
          dataManager = new BuildDataManager(dataStorageRoot, myKeepTempCachesInMemory);
          if (dataManager.versionDiffers()) {
            forceCleanCaches = true;
            msgHandler.processMessage(new CompilerMessage("compile-server", BuildMessage.Kind.INFO, "Dependency data format has changed, project rebuild required"));
          }
        }
        catch (Exception e) {
          // second try
          LOG.info(e);
          if (timestamps != null) {
            timestamps.close();
          }
          if (dataManager != null) {
            dataManager.close();
          }
          forceCleanCaches = true;
          FileUtil.delete(dataStorageRoot);
          timestamps = new ProjectTimestamps(dataStorageRoot);
          dataManager = new BuildDataManager(dataStorageRoot, myKeepTempCachesInMemory);
          // second attempt succeded
          msgHandler.processMessage(new CompilerMessage("compile-server", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
        }

        pd = new ProjectDescriptor(project, fsState, timestamps, dataManager, BuildLoggingManager.DEFAULT);
        myProjects.put(projectPath, pd);
      }
      pd.incUsageCounter();
    }

    final ProjectDescriptor finalPd = pd;
    assert pd.timestamps != null;
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        if (forceCleanCaches && modules.isEmpty() && paths.isEmpty()) {
          // if compilation scope is the whole project and cache rebuild is forced, use PROJECT_REBUILD for faster compilation
          buildType = BuildType.PROJECT_REBUILD;
        }

        final Timestamps timestamps = pd.timestamps.getStorage();
        final CompileScope compileScope = createCompilationScope(buildType, pd, timestamps, modules, artifacts, paths);
        final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), timestamps, builderParams, cs);
        builder.addMessageHandler(msgHandler);
        try {
          switch (buildType) {
            case PROJECT_REBUILD:
              builder.build(compileScope, false, true, forceCleanCaches);
              break;

            case FORCED_COMPILATION:
              builder.build(compileScope, false, false, forceCleanCaches);
              break;

            case MAKE:
              builder.build(compileScope, true, false, forceCleanCaches);
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
            forceCleanCaches = true;
          }
          else {
            throw e;
          }
        }
      }
    }
    finally {
      pd.release();
      clearZipIndexCache();
    }
  }

  private static CompileScope createCompilationScope(BuildType buildType,
                                                     ProjectDescriptor pd,
                                                     final Timestamps timestamps,
                                                     Set<String> modules,
                                                     Collection<String> artifactNames,
                                                     Collection<String> paths) throws Exception {
    Set<Artifact> artifacts = new HashSet<Artifact>();
    if (artifactNames.isEmpty() && buildType == BuildType.PROJECT_REBUILD) {
      artifacts.addAll(pd.project.getArtifacts().values());
    }
    else {
      final Map<String, Artifact> artifactMap = pd.project.getArtifacts();
      for (String name : artifactNames) {
        final Artifact artifact = artifactMap.get(name);
        if (artifact != null && !StringUtil.isEmpty(artifact.getOutputPath())) {
          artifacts.add(artifact);
        }
      }
    }

    final CompileScope compileScope;
    if (buildType == BuildType.PROJECT_REBUILD || (modules.isEmpty() && paths.isEmpty())) {
      compileScope = new AllProjectScope(pd.project, artifacts, buildType != BuildType.MAKE);
    }
    else {
      final Set<Module> forcedModules;
      if (!modules.isEmpty()) {
        forcedModules = new HashSet<Module>();
        for (Module m : pd.project.getModules().values()) {
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
          final RootDescriptor rd = pd.rootsIndex.getModuleAndRoot(file);
          if (rd != null) {
            Set<File> files = filesToCompile.get(rd.module);
            if (files == null) {
              files = new HashSet<File>();
              filesToCompile.put(rd.module, files);
            }
            files.add(file);
            if (buildType == BuildType.FORCED_COMPILATION) {
              pd.fsState.markDirty(file, rd, timestamps);
            }
          }
        }
      }
      else {
        filesToCompile = Collections.emptyMap();
      }

      if (filesToCompile.isEmpty()) {
        compileScope = new ModulesScope(pd.project, forcedModules, artifacts, buildType != BuildType.MAKE);
      }
      else {
        compileScope = new ModulesAndFilesScope(pd.project, forcedModules, filesToCompile, artifacts, buildType != BuildType.MAKE);
      }
    }
    return compileScope;
  }


  private static boolean ourCleanupFailed = false;

  private static void clearZipIndexCache() {
    if (!ourCleanupFailed) {
      try {
        final Class<?> indexClass = Class.forName("com.sun.tools.javac.zip.ZipFileIndex");
        final Method clearMethod = indexClass.getMethod("clearCache");
        clearMethod.invoke(null);
      }
      catch (Throwable ex) {
        ourCleanupFailed = true;
        LOG.info("Method com.sun.tools.javac.zip.ZipFileIndex.clearCache() not found");
      }
    }
  }

  private Project loadProject(String projectPath) {
    final long start = System.currentTimeMillis();
    try {
      final Project project = new Project();
      // setup JDKs and global libraries
      final MethodClosure fakeClosure = new MethodClosure(new Object(), "hashCode");
      for (GlobalLibrary library : myGlobalLibraries) {
        if (library instanceof SdkLibrary) {
          final SdkLibrary sdk = (SdkLibrary)library;
          Node additionalData = null;
          final String additionalXml = sdk.getAdditionalDataXml();
          if (additionalXml != null) {
            try {
              additionalData = new XmlParser(false, false).parseText(additionalXml);
            }
            catch (Exception e) {
              LOG.info(e);
            }
          }
          final Sdk jdk = project.createSdk(sdk.getTypeName(), sdk.getName(), sdk.getVersion(), sdk.getHomePath(), additionalData);
          if (jdk != null) {
            jdk.setClasspath(sdk.getPaths());
          }
          else {
            LOG.info("Failed to load SDK " + sdk.getName() + ", type: " + sdk.getTypeName());
          }
        }
        else {
          final Library lib = project.createGlobalLibrary(library.getName(), fakeClosure);
          if (lib != null) {
            lib.setClasspath(library.getPaths());
          }
          else {
            LOG.info("Failed to load global library " + library.getName());
          }
        }
      }

      final File projectFile = new File(projectPath);

      //String root = dirBased ? projectPath : projectFile.getParent();

      final String loadPath = isDirectoryBased(projectFile) ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
      IdeaProjectLoader.loadFromPath(project, loadPath, myPathVariables, getStartupScript(), new SystemOutErrorReporter(false));
      final String globalEncoding = myGlobalEncoding;
      if (globalEncoding != null && project.getProjectCharset() == null) {
        project.setProjectCharset(globalEncoding);
      }
      project.getIgnoredFilePatterns().loadFromString(myIgnoredFilesPatterns);
      return project;
    }
    finally {
      final long loadTime = System.currentTimeMillis() - start;
      LOG.info("Project " + projectPath + " loaded in " + loadTime + " ms");
    }
  }

  private static boolean isDirectoryBased(File projectFile) {
    return !(projectFile.isFile() && projectFile.getName().endsWith(".ipr"));
  }

  private String getStartupScript() {
    //return "import org.jetbrains.jps.*\n";
    return null;
  }

  private static class InstanceHolder {
    static final ServerState ourInstance = new ServerState();
  }

  public static ServerState getInstance() {
    return InstanceHolder.ourInstance;
  }

}
