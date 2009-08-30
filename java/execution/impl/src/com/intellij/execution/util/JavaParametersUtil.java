package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
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
    Module module = null;
    if (configuration instanceof ModuleBasedConfiguration) {
      module = ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
    }
    String vmParameters = configuration.getProperty(RunJavaConfiguration.VM_PARAMETERS_PROPERTY);
    if (vmParameters != null) {
      vmParameters = expandPath(vmParameters, module, project);
    }
    if (parameters.getEnv() != null) {
      final Map<String, String> envs = new HashMap<String, String>();
      for (String env : parameters.getEnv().keySet()) {
        final String value = expandPath(parameters.getEnv().get(env), module, project);
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
    parameters.setWorkingDirectory(expandPath(workingDirectory, module, project));
  }

  private static String expandPath(String path, Module module, Project project) {
    path = PathMacroManager.getInstance(project).expandPath(path);
    if (module != null) {
      path = PathMacroManager.getInstance(module).expandPath(path);
    }
    return path;

  }

  public static int getClasspathType(final RunConfigurationModule configurationModule, final String mainClassName,
                                     final boolean classMustHaveSource) throws CantRunException {
    final Module module = configurationModule.getModule();
    if (module == null) throw CantRunException.noModuleConfigured(configurationModule.getModuleName());
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(module, mainClassName);
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

  private static Sdk createModuleJdk(final Module module, final String jreHome) throws CantRunException {
    return jreHome == null ? JavaParameters.getModuleJdk(module) : createAlternativeJdk(jreHome);
  }

  private static Sdk createProjectJdk(final Project project, final String jreHome) throws CantRunException {
    return jreHome == null ? createProjectJdk(project) : createAlternativeJdk(jreHome);
  }

  private static Sdk createProjectJdk(final Project project) throws CantRunException {
    final Sdk jdk = PathUtilEx.getAnyJdk(project);
    if (jdk == null) {
      throw CantRunException.noJdkConfigured();
    }
    return jdk;
  }

  private static Sdk createAlternativeJdk(final String jreHome) throws CantRunException {
    final Sdk jdk = JavaSdk.getInstance().createJdk("", jreHome);
    if (jdk == null) throw CantRunException.noJdkConfigured();
    return jdk;
  }
}
