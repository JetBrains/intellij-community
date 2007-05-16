package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * User: lex
 * Date: Nov 26, 2003
 * Time: 10:38:01 PM
 */
public class JavaParametersUtil {
  public static void configureConfiguration(final JavaParameters parameters, final RunJavaConfiguration configuration) {
    final Project project = configuration.getProject();
    parameters.getProgramParametersList().addParametersString(configuration.getProperty(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY));
    final PathMacroManager macroManager = PathMacroManager.getInstance(project);
    String vmParameters = configuration.getProperty(RunJavaConfiguration.VM_PARAMETERS_PROPERTY);
    if (vmParameters != null) {
      vmParameters = macroManager.expandPath(vmParameters);
    }
    if (parameters.getEnv() != null) {
      final Map<String, String> envs = new HashMap<String, String>();
      for (String env : parameters.getEnv().keySet()) {
        final String value = macroManager.expandPath(parameters.getEnv().get(env));
        envs.put(env, value);
        if (vmParameters != null) {
          vmParameters = StringUtil.replace(vmParameters, "$" + env + "$", value, false); //replace env usages
        }
      }
      parameters.setEnv(envs);
    }
    parameters.getVMParametersList().addParametersString(vmParameters);
    String workingDirectory = configuration.getProperty(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY);
    if (workingDirectory == null || workingDirectory.trim().length() == 0) {
      workingDirectory = PathUtil.getLocalPath(project.getBaseDir());
    }
    parameters.setWorkingDirectory(workingDirectory);
  }

  public static int getClasspathType(final RunConfigurationModule configurationModule, final String mainClassName,
                                     final boolean classMustHaveSource) throws CantRunException {
    final Module module = configurationModule.getModule();
    if (module == null) throw CantRunException.noModuleConfigured(configurationModule.getModuleName());
    final PsiClass psiClass = ExecutionUtil.findMainClass(module, mainClassName);
    if (psiClass == null)  {
      if ( ! classMustHaveSource ) return JavaParameters.JDK_AND_CLASSES_AND_TESTS;
      throw CantRunException.classNotFound(mainClassName, module);
    }
    final PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile == null) throw CantRunException.classNotFound(mainClassName, module);
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) throw CantRunException.classNotFound(mainClassName, module);
    Module classModule = new JUnitUtil.ModuleOfClass().convert(psiClass);
    if (classModule == null) classModule = module;
    return ModuleRootManager.getInstance(classModule).getFileIndex().
      isInTestSourceContent(virtualFile) ? JavaParameters.JDK_AND_CLASSES_AND_TESTS : JavaParameters.JDK_AND_CLASSES;
  }

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
