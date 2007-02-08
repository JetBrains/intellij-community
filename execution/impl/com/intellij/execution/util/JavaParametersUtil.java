package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.util.PathUtil;

/**
 * User: lex
 * Date: Nov 26, 2003
 * Time: 10:38:01 PM
 */
public class JavaParametersUtil {
  public static void configureConfiguration(final JavaParameters parameters, final RunJavaConfiguration configuration) {
    parameters.getProgramParametersList().addParametersString(configuration.getProperty(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY));
    parameters.getVMParametersList().addParametersString(configuration.getProperty(RunJavaConfiguration.VM_PARAMETERS_PROPERTY));
    String workingDirectory = configuration.getProperty(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY);
    if (workingDirectory == null || workingDirectory.trim().length() == 0) {
      workingDirectory = PathUtil.getLocalPath(configuration.getProject().getBaseDir());
    }
    parameters.setWorkingDirectory(workingDirectory);
  }

  static final boolean newWay = true;

  public static void configureModule(final RunConfigurationModule runConfigurationModule,
                                     final JavaParameters parameters,
                                     final int classPathType,
                                     final String jreHome) throws CantRunException {
    Module module = runConfigurationModule.getModule();
    if (module == null) {
      throw CantRunException.noModuleConfigured(runConfigurationModule.getModuleName());
    }
    parameters.configureByModule(module, classPathType, createModuleJdk(module, jreHome));
  }

  public static void configureProject(Project project, final JavaParameters parameters, final int classPathType, final String jreHome) throws CantRunException {
    parameters.configureByProject(project, classPathType, createProjectJdk(project, jreHome));
  }

  private static ProjectJdk createModuleJdk(final Module module, final String jreHome) throws CantRunException {
    return jreHome == null ? JavaParameters.getModuleJdk(module) : createAlternativeJdk(jreHome);
  }

  private static ProjectJdk createProjectJdk(final Project project, final String jreHome) throws CantRunException {
    return jreHome == null ? createProjectJdk(project) : createAlternativeJdk(jreHome);
  }

  private static ProjectJdk createProjectJdk(final Project project) throws CantRunException {
    final ProjectJdk jdk = PathUtilEx.getAnyJdk(project);
    if (jdk == null) {
      throw CantRunException.noJdkConfigured();
    }
    return jdk;
  }

  private static ProjectJdk createAlternativeJdk(final String jreHome) throws CantRunException {
    final ProjectJdk jdk = JavaSdk.getInstance().createJdk("", jreHome);
    if (jdk == null) throw CantRunException.noJdkConfigured();
    return jdk;
  }

  @Deprecated
  public static void configureClassPath(final JavaParameters parameters, Project project, final String jreHome) throws CantRunException {
    configureProject(project, parameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jreHome);
  }
  /*
    final List<CommandLineEntry> common = getClassPath(project);

    final ProjectJdk jdk;
    if (jreHome == null) {
      jdk = PathUtilEx.getAnyJdk(project);
      if (jdk == null) throw CantRunException.noJdkConfigured();
    } else {
      jdk = JavaSdk.getInstance().createJdk("", jreHome);
      if (jdk == null) throw CantRunException.noJdkConfigured();
    }

    parameters.setJdk(jdk);
    for (final CommandLineEntry entry : common) {
      entry.addPath(parameters, jdk);
    }
  }

  private static List<CommandLineEntry> getClassPath(Project project) {
    final List<List<CommandLineEntry>> orders = new ArrayList<List<CommandLineEntry>>();

    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      orders.add(getClassPath(module, false));
    }
    return OrdersMerger.mergeOrder(orders, (TObjectHashingStrategy<CommandLineEntry>)TObjectHashingStrategy.CANONICAL);
  }

  private static List<CommandLineEntry> getClassPath(final Module module, final boolean withDependencies) {
    final Set<Module> alreadyVisited = new HashSet<Module>();
    final List<CommandLineEntry> entries = new ArrayList<CommandLineEntry>();
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    moduleRootManager.processOrder(new RootPolicy<Module>() {
      public Module visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final Module module) {
        entries.add(JavaParametersUtil.JDK_ENTRY);
        return module;
      }

      public Module visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final Module module) {
        addUrls(Arrays.asList(libraryOrderEntry.getUrls(OrderRootType.CLASSES_AND_OUTPUT)));
        return module;
      }

      public Module visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final Module module) {
        if (withDependencies) {
          final Module moduleDep = moduleOrderEntry.getModule();
          if ( ! alreadyVisited.contains ( moduleDep ) ) {
            alreadyVisited.add( moduleDep );
            ModuleRootManager.getInstance(moduleDep).processOrder(this,moduleDep);
          }
        }
        return module;
      }

      public Module visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry, final Module module) {
        addUrls(ProjectRootsTraversing.RootTraversePolicy.ALL_OUTPUTS.getOutputs(module));
        return module;
      }

      private void addUrls(final Iterable<String> urls) {
        for (final String url : urls) {
          entries.add(new ClassPathEntry(PathUtil.toPresentableUrl(url)));
        }
      }
    }, module);
    return entries;
  }

  private interface CommandLineEntry {
    void addPath(JavaParameters parameters, ProjectJdk jdk);
  }

  private static final CommandLineEntry JDK_ENTRY = new CommandLineEntry() {
    public void addPath(final JavaParameters parameters, final ProjectJdk jdk) {
      parameters.setJdk(jdk);
      final List<String> jdkPaths = ContainerUtil.map(jdk.getRootProvider().getUrls(OrderRootType.CLASSES_AND_OUTPUT), URL_TO_LOCAL_PATH);
      for (final String jdkPath : jdkPaths) {
        parameters.getClassPath().add(jdkPath);
      }
    }
  };

  public static final Function<String, String> URL_TO_LOCAL_PATH = new Function<String, String>() {
    public String fun(final String url) {
      return PathUtil.toPresentableUrl(url);
    }
  };

  private static class ClassPathEntry implements CommandLineEntry {
    private final String myClassPath;

    public ClassPathEntry(final String path) {
      myClassPath = path;
    }

    public boolean equals(final Object object) {
      if (!(object instanceof ClassPathEntry)) return false;
      final String otherPath = ((ClassPathEntry)object).myClassPath;
      return SystemInfo.isFileSystemCaseSensitive ? otherPath.equals(myClassPath) : otherPath.equalsIgnoreCase(myClassPath);
    }

    public int hashCode() {
      return myClassPath.hashCode();
    }

    public void addPath(final JavaParameters parameters, final ProjectJdk jdk) {
      parameters.getClassPath().add(myClassPath);
    }
  }
  */
}
