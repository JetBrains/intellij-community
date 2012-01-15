package org.jetbrains.jps.server;

import com.intellij.openapi.diagnostic.Logger;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.BuildParameters;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.GlobalLibrary;
import org.jetbrains.jps.api.SdkLibrary;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;

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

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars) {
    synchronized (myConfigurationLock) {
      for (Map.Entry<String, ProjectDescriptor> entry : myProjects.entrySet()) {
        final String projectPath = entry.getKey();
        final ProjectDescriptor descriptor = entry.getValue();
        descriptor.release();
      }
      myProjects.clear(); // projects should be reloaded against the latest data
      myGlobalLibraries.clear();
      myGlobalLibraries.addAll(libs);
      myPathVariables.clear();
      myPathVariables.putAll(pathVars);
    }
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

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessageHandler msgHandler, BuildCanceledStatus cs) throws Throwable{
    final String projectName = getProjectName(projectPath);
    BuildType buildType = params.buildType;

    ProjectDescriptor pd;
    synchronized (myConfigurationLock) {
      pd = myProjects.get(projectPath);
      if (pd == null) {
        final Project project = loadProject(projectPath, params);
        final FSState fsState = new FSState();
        pd = new ProjectDescriptor(projectName, project, fsState, new ProjectTimestamps(projectName));
        myProjects.put(projectPath, pd);
      }
      pd.incUsageCounter();
    }

    final Project project = pd.project;

    try {
      final List<Module> toCompile = new ArrayList<Module>();
      if (modules != null && modules.size() > 0) {
        for (Module m : project.getModules().values()) {
          if (modules.contains(m.getName())){
            toCompile.add(m);
          }
        }
      }
      else {
        toCompile.addAll(project.getModules().values());
      }

      final CompileScope compileScope = new CompileScope(project, toCompile);

      final IncProjectBuilder builder = new IncProjectBuilder(pd, BuilderRegistry.getInstance(), cs);
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

  private static void clearZipIndexCache() {
    try {
      final Class<?> indexClass = Class.forName("com.sun.tools.javac.zip.ZipFileIndex");
      final Method clearMethod = indexClass.getMethod("clearCache");
      clearMethod.invoke(null);
    }
    catch (Throwable ex) {
      LOG.info(ex);
    }
  }

  private static String getProjectName(String projectPath) {
    final File path = new File(projectPath);
    final String name = path.getName().toLowerCase(Locale.US);
    if (!isDirectoryBased(path) && name.endsWith(".ipr")) {
      return name.substring(0, name.length() - ".ipr".length());
    }
    return name;
  }

  private Project loadProject(String projectPath, BuildParameters params) {
    final Project project = new Project();
    // setup JDKs and global libraries
    final MethodClosure fakeClosure = new MethodClosure(new Object(), "hashCode");
    for (GlobalLibrary library : myGlobalLibraries) {
      if (library instanceof SdkLibrary) {
        final SdkLibrary sdk = (SdkLibrary)library;
        final JavaSdk jdk = project.createJavaSdk(sdk.getName(), sdk.getHomePath(), fakeClosure);
        jdk.setClasspath(sdk.getPaths());
      }
      else {
        final Library lib = project.createGlobalLibrary(library.getName(), fakeClosure);
        lib.setClasspath(library.getPaths());
      }
    }

    final File projectFile = new File(projectPath);

    //String root = dirBased ? projectPath : projectFile.getParent();

    final String loadPath = isDirectoryBased(projectFile) ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
    IdeaProjectLoader.loadFromPath(project, loadPath, myPathVariables, getStartupScript());
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
