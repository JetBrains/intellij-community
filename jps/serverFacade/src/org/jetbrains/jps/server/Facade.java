package org.jetbrains.jps.server;

import org.codehaus.gant.GantBinding;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
public class Facade {
  public static final String IDEA_PROJECT_DIRNAME = ".idea";

  private final Map<String, Project> myProjects = new HashMap<String, Project>();
  private final Map<String, String> myPathVariables = new ConcurrentHashMap<String, String>();

  public void setPathVariables(Map<String, String> vars) {
    myPathVariables.clear();
    myPathVariables.putAll(vars);
  }

  public void clearProjectCache(String projectPath) {
    myProjects.remove(projectPath);
  }

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessagesConsumer consumer) throws Throwable {
    Project project = myProjects.get(projectPath);

    if (project == null) {
      project = loadProject(projectPath, params);
      myProjects.put(projectPath, project);
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

    final IncProjectBuilder builder = new IncProjectBuilder(project, BuilderRegistry.getInstance());
    builder.addMessageHandler(new MessageHandler() {
      public void processMessage(BuildMessage msg) {
        if (msg instanceof CompilerMessage) {
          consumer.consumeCompilerMessage(((CompilerMessage)msg).getCompilerName(), msg.getMessageText());
        }
        else {
          consumer.consumeProgressMessage(msg.getMessageText());
        }
      }
    });
    switch (params.buildType) {
      case REBUILD:
        builder.build(compileScope, false);
        break;

      case MAKE:
        builder.build(compileScope, true);
        break;

      case CLEAN:
        project.clean();
        break;
    }
    //pw.save();
  }

  private Project loadProject(String projectPath, BuildParameters params) {
    final Project project = new Project(new GantBinding());
    // setup JDKs and global libraries
    final MethodClosure fakeClosure = new MethodClosure(new Object(), "hashCode");
    for (GlobalLibrary library : params.globalLibraries) {
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
    final boolean dirBased = !(projectFile.isFile() && projectPath.endsWith(".ipr"));

    //String root = dirBased ? projectPath : projectFile.getParent();

    final String loadPath = dirBased ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
    IdeaProjectLoader.loadFromPath(project, loadPath, myPathVariables, getStartupScript());
    return project;
  }

  private String getStartupScript() {
    return "import org.jetbrains.jps.*\n";
      //"\n" +
      //"project.createJavaSdk (\n" +
      //"   \"IDEA jdk\", \n" +
      //"   \"/home/db/develop/jetbrains/jdk1.6.0_22\", \n" +
      //"   {      \n" +
      //"     getDelegate ().classpath (\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/plugin.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/charsets.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/jce.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/rt.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/management-agent.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/resources.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/deploy.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/jsse.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/javaws.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/alt-rt.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/sunpkcs11.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/dnsns.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/localedata.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/jre/lib/ext/sunjce_provider.jar\",\n" +
      //"       \"/home/db/develop/jetbrains/jdk1.6.0_22/lib/tools.jar\"\n" +
      //"     )\n" +
      //"   }\n" +
      //")\n" +
      //"\n" +
      //"project.projectSdk = project.sdks [\"IDEA jdk\"]\n" +
  }

  private static class InstanceHolder {
    static final Facade ourInstance = new Facade();
  }

  public static Facade getInstance() {
    return InstanceHolder.ourInstance;
  }

}
