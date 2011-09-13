package org.jetbrains.jps.server;

import org.apache.tools.ant.BuildEvent;
import org.codehaus.gant.GantBinding;
import org.jetbrains.ether.ProjectWrapper;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.listeners.BuildInfoPrinter;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
public class Facade {
  private final Map<String, ProjectWrapper> myProjects = new HashMap<String, ProjectWrapper>();
  private final Map<String, String> myPathVariables = new ConcurrentHashMap<String, String>();

  public void setPathVariables(Map<String, String> vars) {
    myPathVariables.clear();
    myPathVariables.putAll(vars);
  }

  public void clearProjectCache(String projectPath) {
    myProjects.remove(projectPath);
  }

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessagesConsumer consumer) throws Throwable {
    ProjectWrapper pw = myProjects.get(projectPath);
    if (pw == null) {
      pw = ProjectWrapper.load(new GantBinding(), projectPath, getStartupScript(), myPathVariables, true);
      myProjects.put(projectPath, pw);
    }

    configureProject(pw, params, consumer);

    List<Module> toCompile = null;
    if (modules != null && modules.size() > 0) {
      for (Module m : pw.getProject().getModules().values()) {
        if (modules.contains(m.getName())){
          if (toCompile == null) {
            toCompile = new ArrayList<Module>();
          }
          toCompile.add(m);
        }
      }
    }

    switch (params.buildType) {
      case REBUILD:
        if (toCompile == null || toCompile.isEmpty()) {
          pw.rebuild();
        }
        else {
          pw.makeModules(toCompile, new ProjectWrapper.Flags() {
            public boolean tests() {
              return true;
            }

            public boolean incremental() {
              return false;
            }

            public boolean force() {
              return true;
            }

            public PrintStream logStream() {
              return null; // todo
            }
          });
        }
        break;

      case MAKE:
        pw.makeModules(toCompile, new ProjectWrapper.Flags() {
          public boolean tests() {
            return true;
          }

          public boolean incremental() {
            return true;
          }

          public boolean force() {
            return false;
          }

          public PrintStream logStream() {
            return null; // todo
          }
        });
        break;

      case CLEAN:
        pw.clean();
        break;
    }
    pw.save();
  }

  private void configureProject(ProjectWrapper pw, BuildParameters params, final MessagesConsumer consumer) {
    pw.getProject().getBuilder().setUseInProcessJavac(params.useInProcessJavac);
    pw.getProject().getBuilder().setBuildInfoPrinter(new BuildInfoPrinter() {
      public Object printProgressMessage(Project project, String message) {
        consumer.consumeProgressMessage(message);
        return null;
      }

      public Object printCompilationErrors(Project project, String compilerName, String messages) {
        consumer.consumeCompilerMessage(compilerName, messages);
        return null;
      }
    });
    pw.getProject().getBinding().addBuildListener(new org.apache.tools.ant.BuildListener() {
      public void buildStarted(BuildEvent buildEvent) {
      }

      public void buildFinished(BuildEvent buildEvent) {
      }

      public void targetStarted(BuildEvent buildEvent) {
      }

      public void targetFinished(BuildEvent buildEvent) {
      }

      public void taskStarted(BuildEvent buildEvent) {
      }

      public void taskFinished(BuildEvent buildEvent) {
      }

      public void messageLogged(BuildEvent buildEvent) {
        consumer.consumeCompilerMessage("ANT", buildEvent.getMessage());
      }
    });
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
