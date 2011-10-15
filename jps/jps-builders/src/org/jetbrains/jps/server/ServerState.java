package org.jetbrains.jps.server;

import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.BuildParameters;
import org.jetbrains.jps.api.GlobalLibrary;
import org.jetbrains.jps.api.SdkLibrary;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.MessageHandler;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
class ServerState {
  public static final String IDEA_PROJECT_DIRNAME = ".idea";

  private final Map<String, Project> myProjects = new HashMap<String, Project>();
  private final Object myConfigurationLock = new Object();
  private final Map<String, String> myPathVariables = new HashMap<String, String>();
  private final List<GlobalLibrary> myGlobalLibraries = new ArrayList<GlobalLibrary>();

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars) {
    synchronized (myConfigurationLock) {
      myProjects.clear(); // projects should be reloaded against the latest data
      myGlobalLibraries.clear();
      myGlobalLibraries.addAll(libs);
      myPathVariables.clear();
      myPathVariables.putAll(pathVars);
    }
  }

  public void clearProjectCache(Collection<String> projectPaths) {
    synchronized (myConfigurationLock) {
      myProjects.keySet().removeAll(projectPaths);
    }
  }

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessageHandler msgHandler) throws Throwable{
    Project project;

    synchronized (myConfigurationLock) {
      project = myProjects.get(projectPath);
      if (project == null) {
        project = loadProject(projectPath, params);
        myProjects.put(projectPath, project);
      }
    }

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

    final CompileScope compileScope = new CompileScope(project) {
      public Collection<Module> getAffectedModules() {
        return toCompile;
      }
    };

    final IncProjectBuilder builder = new IncProjectBuilder(project, getProjectName(projectPath), BuilderRegistry.getInstance());
    if (msgHandler != null) {
      builder.addMessageHandler(msgHandler);
    }
    switch (params.buildType) {
      case REBUILD:
        builder.build(compileScope, false);
        break;

      case MAKE:
        builder.build(compileScope, true);
        break;

      case CLEAN:
        //todo[nik]
//        new ProjectBuilder(new GantBinding(), project).clean();
        break;
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
