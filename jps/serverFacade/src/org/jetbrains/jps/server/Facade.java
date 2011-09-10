package org.jetbrains.jps.server;

import org.codehaus.gant.GantBinding;
import org.jetbrains.ether.ProjectWrapper;
import org.jetbrains.jps.Module;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 */
public class Facade {
  static enum BuildType {
    REBUILD, MAKE, CLEAN
  }

  public static class BuildParameters {
    BuildType buildType = BuildType.MAKE;
    Map<String,String> pathVariables;
    boolean useInProcessJavac = true;

    public BuildParameters() {
    }

    public BuildParameters(BuildType buildType) {
      this.buildType = buildType;
    }
  }

  public void startBuild(String projectPath, Set<String> modules, BuildParameters params) throws Throwable {
    final ProjectWrapper proj = ProjectWrapper.load(new GantBinding(), projectPath, getStartupScript(params), params.pathVariables, params.buildType == BuildType.MAKE);

    List<Module> toCompile = null;
    if (modules != null && modules.size() > 0) {
      for (Module m : proj.getProject().getModules().values()) {
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
          proj.rebuild();
        }
        else {
          proj.makeModules(toCompile, new ProjectWrapper.Flags() {
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
        proj.makeModules(toCompile, new ProjectWrapper.Flags() {
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
        proj.clean();
        break;
    }
    proj.save();
  }

  private String getStartupScript(BuildParameters params) {
    return "import org.jetbrains.jps.*\n" +
      "\n" +
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
      "project.builder.useInProcessJavac = " + Boolean.valueOf(params.useInProcessJavac).toString();
  }

  private static class InstanceHolder {
    static final Facade ourInstance = new Facade();
  }

  public static Facade getInstance() {
    return InstanceHolder.ourInstance;
  }

}
