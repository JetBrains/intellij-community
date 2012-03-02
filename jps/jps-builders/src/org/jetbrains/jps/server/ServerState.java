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
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

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

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars, String globalEncoding) {
    synchronized (myConfigurationLock) {
      clearCahedState();
      myGlobalLibraries.addAll(libs);
      myPathVariables.putAll(pathVars);
      myGlobalEncoding = StringUtil.isEmpty(globalEncoding)? null : globalEncoding;
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


    ProjectDescriptor pd;
    synchronized (myConfigurationLock) {
      pd = myProjects.get(projectPath);
      if (pd == null) {
        final Project project = loadProject(projectPath);
        final FSState fsState = new FSState(false);
        ProjectTimestamps timestamps = null;
        BuildDataManager dataManager = null;
        final File dataStorageRoot = Paths.getDataStorageRoot(project);
        try {
          timestamps = new ProjectTimestamps(dataStorageRoot);
          dataManager = new BuildDataManager(dataStorageRoot, myKeepTempCachesInMemory);
        }
        catch (Exception e) {
          // second try
          e.printStackTrace(System.err);
          if (timestamps != null) {
            timestamps.close();
          }
          if (dataManager != null) {
            dataManager.close();
          }
          buildType = BuildType.PROJECT_REBUILD; // force project rebuild
          FileUtil.delete(dataStorageRoot);
          timestamps = new ProjectTimestamps(dataStorageRoot);
          dataManager = new BuildDataManager(dataStorageRoot, myKeepTempCachesInMemory);
          // second attempt succeded
          msgHandler.processMessage(new CompilerMessage("compile-server", BuildMessage.Kind.INFO, "Project rebuild forced: " + e.getMessage()));
        }

        pd = new ProjectDescriptor(project, fsState, timestamps, dataManager);
        myProjects.put(projectPath, pd);
      }
      pd.incUsageCounter();
    }

    final Project project = pd.project;

    try {
      final CompileScope compileScope = createCompilationScope(buildType, pd, modules, artifacts, paths);
      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), builderParams, cs);
      if (msgHandler != null) {
        builder.addMessageHandler(msgHandler);
      }
      switch (buildType) {
        case PROJECT_REBUILD:
          builder.build(compileScope, false, true);
          break;

        case FORCED_COMPILATION:
          builder.build(compileScope, false, false);
          break;

        case MAKE:
          builder.build(compileScope, true, false);
          break;

        case CLEAN:
          //todo[nik]
  //        new ProjectBuilder(new GantBinding(), project).clean();
          break;
      }
    }
    finally {
      pd.release();
      clearZipIndexCache();
    }
  }

  private static CompileScope createCompilationScope(BuildType buildType, ProjectDescriptor pd, Set<String> modules,
                                                     Collection<String> artifactNames, Collection<String> paths) throws Exception {
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

      final TimestampStorage tsStorage = pd.timestamps.getStorage();

      final Map<Module, Set<File>> filesToCompile;
      if (!paths.isEmpty()) {
        filesToCompile = new HashMap<Module, Set<File>>();
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
              pd.fsState.markDirty(file, rd, tsStorage);
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
        LOG.info(ex);
      }
    }
  }

  private Project loadProject(String projectPath) {
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
        final Sdk jdk = project.createSdk(/*"JavaSDK"*/sdk.getTypeName(), sdk.getName(), sdk.getHomePath(), additionalData);
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
    return project;
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
