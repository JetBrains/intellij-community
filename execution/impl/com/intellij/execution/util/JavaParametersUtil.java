package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.OrdersMerger;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;
import java.util.List;

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
      final VirtualFile projectFile = configuration.getProject().getProjectFile();
      if (projectFile != null) {
        workingDirectory = PathUtil.getLocalPath(projectFile.getParent());
      }
    }
    parameters.setWorkingDirectory(workingDirectory);
  }

  public static void configureModule(final RunConfigurationModule runConfigurationModule,
                                     final JavaParameters parameters,
                                     final int classPathType,
                                     final String jreHome) throws CantRunException {
    if(runConfigurationModule.getModule() == null) {
      throw CantRunException.noModuleConfigured(runConfigurationModule.getModuleName());
    }
    if (jreHome == null){
      parameters.configureByModule(runConfigurationModule.getModule(), classPathType);
    } else {
      configureOneModuleClassPath(parameters, runConfigurationModule.getModule(), jreHome);
    }
  }

  private static void configureOneModuleClassPath(final  JavaParameters parameters, Module module, String jreHome) throws CantRunException {
    final List<CommandLineEntry> common = getClassPath(module, true);

    final ProjectJdk jdk = JavaSdk.getInstance().createJdk("", jreHome);
    if (jdk == null) throw CantRunException.noJdkConfigured();

    parameters.setJdk(jdk);
    for (final CommandLineEntry entry : common) {
      entry.addPath(parameters, jdk);
    }
  }

  public static void configureClassPath(final JavaParameters parameters, Project project, final String jreHome) throws CantRunException {
    final List<CommandLineEntry> common = composeClassPath(project);

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

  private static List<CommandLineEntry> composeClassPath(Project project) {
    final ArrayList<ArrayList<CommandLineEntry>> orders = new ArrayList<ArrayList<CommandLineEntry>>();

    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      orders.add(getClassPath(module, false));
    }
    return OrdersMerger.mergeOrder(orders, (TObjectHashingStrategy<CommandLineEntry>)TObjectHashingStrategy.CANONICAL);
  }

  private static ArrayList<CommandLineEntry> getClassPath(final Module module, final boolean withDependencies) {
    final ArrayList<CommandLineEntry> entries = new ArrayList<CommandLineEntry>();
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    moduleRootManager.processOrder(new RootPolicy<Object>() {
      public Object visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final Object object) {
        entries.add(JavaParametersUtil.JDK_ENTRY);
        return null;
      }

      public Object visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry, final Object object) {
        final String[] urls = libraryOrderEntry.getUrls(OrderRootType.CLASSES_AND_OUTPUT);
        for (final String url : urls) {
          entries.add(new ClassPathEntry(PathUtil.toPresentableUrl(url)));
        }
        return null;
      }

      public Object visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry, final Object object) {
        final List<String> outputs = ProjectRootsTraversing.RootTraversePolicy.ALL_OUTPUTS.getOutputs(module);
        if (withDependencies){
          final Module[] dependencies = moduleRootManager.getDependencies();
          if (dependencies != null){
            for (Module module1 : dependencies) {
              outputs.addAll(ProjectRootsTraversing.RootTraversePolicy.ALL_OUTPUTS.getOutputs(module1));
              final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module1).getOrderEntries();
              for (OrderEntry entry : orderEntries) {
                if (entry instanceof LibraryOrderEntry){
                  final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
                  if (libraryOrderEntry.isExported()){
                    final String[] urls = libraryOrderEntry.getUrls(OrderRootType.CLASSES_AND_OUTPUT);
                    for (final String url : urls) {
                      entries.add(new ClassPathEntry(PathUtil.toPresentableUrl(url)));
                    }
                  }
                }
              }
            }
          }
        }
        for (final String url : outputs) {
          entries.add(new ClassPathEntry(PathUtil.toPresentableUrl(url)));
        }
        return null;
      }
    }, null);
    return entries;
  }

  private interface CommandLineEntry {
    void addPath(JavaParameters parameters, ProjectJdk jdk);
  }

  private static final CommandLineEntry JDK_ENTRY = new CommandLineEntry() {
      public void addPath(final JavaParameters parameters, final ProjectJdk jdk) {
        parameters.setJdk(jdk);
        final List<String> jdkPaths = ContainerUtil.map(jdk.getRootProvider().getUrls(OrderRootType.CLASSES_AND_OUTPUT),
                                                                       URL_TO_LOCAL_PATH);
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
}
