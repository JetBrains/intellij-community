// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * @author lex
 * @since Nov 26, 2003
 */
public class JavaParametersUtil {
  private JavaParametersUtil() { }

  public static void configureConfiguration(SimpleJavaParameters parameters, CommonJavaRunConfigurationParameters configuration) {
    ProgramParametersUtil.configureConfiguration(parameters, configuration);

    Project project = configuration.getProject();
    Module module = ProgramParametersUtil.getModule(configuration);

    String alternativeJrePath = configuration.getAlternativeJrePath();
    if (alternativeJrePath != null) {
      configuration.setAlternativeJrePath(ProgramParametersUtil.expandPath(alternativeJrePath, null, project));
    }

    String vmParameters = configuration.getVMParameters();
    if (vmParameters != null) {
      vmParameters = ProgramParametersUtil.expandPath(vmParameters, module, project);

      for (Map.Entry<String, String> each : parameters.getEnv().entrySet()) {
        vmParameters = StringUtil.replace(vmParameters, "$" + each.getKey() + "$", each.getValue(), false); //replace env usages
      }
    }

    parameters.getVMParametersList().addParametersString(vmParameters);
  }

  @MagicConstant(valuesFromClass = JavaParameters.class)
  public static int getClasspathType(final RunConfigurationModule configurationModule, final String mainClassName,
                                     final boolean classMustHaveSource) throws CantRunException {
    return getClasspathType(configurationModule, mainClassName, classMustHaveSource, false);
  }

  @MagicConstant(valuesFromClass = JavaParameters.class)
  public static int getClasspathType(final RunConfigurationModule configurationModule, final String mainClassName,
                                     final boolean classMustHaveSource, final boolean includeProvidedDependencies) throws CantRunException {
    final Module module = configurationModule.getModule();
    if (module == null) throw CantRunException.noModuleConfigured(configurationModule.getModuleName());
    Boolean inProduction = isClassInProductionSources(mainClassName, module);
    if (inProduction == null) {
      if (!classMustHaveSource) {
        return JavaParameters.JDK_AND_CLASSES_AND_TESTS;
      }
      throw CantRunException.classNotFound(mainClassName, module);
    }

    return inProduction
           ? (includeProvidedDependencies ? JavaParameters.JDK_AND_CLASSES_AND_PROVIDED : JavaParameters.JDK_AND_CLASSES)
           : JavaParameters.JDK_AND_CLASSES_AND_TESTS;
  }

  @Nullable("null if class not found")
  public static Boolean isClassInProductionSources(@NotNull String mainClassName, @NotNull Module module) {
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(module, mainClassName);
    if (psiClass == null) {
      return null;
    }
    final PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile == null) return null;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    Module classModule = psiClass.isValid() ? ModuleUtilCore.findModuleForPsiElement(psiClass) : null;
    if (classModule == null) classModule = module;
    ModuleFileIndex fileIndex = ModuleRootManager.getInstance(classModule).getFileIndex();
    if (fileIndex.isInSourceContent(virtualFile)) {
      return !fileIndex.isInTestSourceContent(virtualFile);
    }
    final List<OrderEntry> entriesForFile = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry entry : entriesForFile) {
      if (entry instanceof ExportableOrderEntry && ((ExportableOrderEntry)entry).getScope() == DependencyScope.TEST) {
        return false;
      }
    }
    return true;
  }

  public static void configureModule(final RunConfigurationModule runConfigurationModule,
                                     final JavaParameters parameters,
                                     @MagicConstant(valuesFromClass = JavaParameters.class) final int classPathType,
                                     @Nullable String jreHome) throws CantRunException {
    Module module = runConfigurationModule.getModule();
    if (module == null) {
      throw CantRunException.noModuleConfigured(runConfigurationModule.getModuleName());
    }
    configureModule(module, parameters, classPathType, jreHome);
  }

  public static void configureModule(Module module,
                                     JavaParameters parameters,
                                     @MagicConstant(valuesFromClass = JavaParameters.class) int classPathType,
                                     @Nullable String jreHome) throws CantRunException {
    parameters.configureByModule(module, classPathType, createModuleJdk(module, (classPathType & JavaParameters.TESTS_ONLY) == 0, jreHome));
  }

  public static void configureProject(Project project,
                                      final JavaParameters parameters,
                                      @MagicConstant(valuesFromClass = JavaParameters.class) final int classPathType,
                                      @Nullable String jreHome) throws CantRunException {
    parameters.configureByProject(project, classPathType, createProjectJdk(project, jreHome));
  }

  public static Sdk createModuleJdk(final Module module, boolean productionOnly, @Nullable String jreHome) throws CantRunException {
    return jreHome == null ? JavaParameters.getValidJdkToRunModule(module, productionOnly) : createAlternativeJdk(jreHome);
  }

  public static Sdk createProjectJdk(final Project project, @Nullable String jreHome) throws CantRunException {
    return jreHome == null ? createProjectJdk(project) : createAlternativeJdk(jreHome);
  }

  private static Sdk createProjectJdk(final Project project) throws CantRunException {
    final Sdk jdk = PathUtilEx.getAnyJdk(project);
    if (jdk == null) {
      throw CantRunException.noJdkConfigured();
    }
    return jdk;
  }

  private static Sdk createAlternativeJdk(@NotNull String jreHome) throws CantRunException {
    final Sdk configuredJdk = ProjectJdkTable.getInstance().findJdk(jreHome);
    if (configuredJdk != null) {
      return configuredJdk;
    }

    if (!JdkUtil.checkForJre(jreHome)) {
      throw new CantRunException(ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.message", jreHome));
    }

    final JavaSdk javaSdk = JavaSdk.getInstance();
    return javaSdk.createJdk(ObjectUtils.notNull(javaSdk.getVersionString(jreHome), ""), jreHome);
  }

  public static void checkAlternativeJRE(@NotNull CommonJavaRunConfigurationParameters configuration) throws RuntimeConfigurationWarning {
    if (configuration.isAlternativeJrePathEnabled()) {
      checkAlternativeJRE(configuration.getAlternativeJrePath());
    }
  }

  public static void checkAlternativeJRE(@Nullable String jrePath) throws RuntimeConfigurationWarning {
    if (StringUtil.isEmptyOrSpaces(jrePath) ||
        ProjectJdkTable.getInstance().findJdk(jrePath) == null && !JdkUtil.checkForJre(jrePath)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.message", jrePath));
    }
  }

  @SuppressWarnings("deprecation")
  @NotNull
  public static DefaultJDOMExternalizer.JDOMFilter getFilter(@NotNull CommonJavaRunConfigurationParameters parameters) {
    return new DefaultJDOMExternalizer.JDOMFilter() {
      @Override
      public boolean isAccept(@NotNull Field field) {
        String name = field.getName();
        if ((name.equals("ALTERNATIVE_JRE_PATH_ENABLED") && !parameters.isAlternativeJrePathEnabled()) ||
            (name.equals("ALTERNATIVE_JRE_PATH") && StringUtil.isEmpty(parameters.getAlternativeJrePath()))) {
          return false;
        }
        return true;
      }
    };
  }
}