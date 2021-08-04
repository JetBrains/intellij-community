// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.execution.CantRunException;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class JavaParametersUtil {
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
      List<String> vmParametersList = ProgramParametersConfigurator.expandMacrosAndParseParameters(vmParameters);
      parameters.getVMParametersList().addAll(vmParametersList);
    }
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
    return jreHome == null ? JavaParameters.getValidJdkToRunModule(module, productionOnly) : createAlternativeJdk(module.getProject(), jreHome);
  }

  public static Sdk createProjectJdk(@NotNull final Project project, @Nullable String jreHome) throws CantRunException {
    return jreHome == null ? createProjectJdk(project) : createAlternativeJdk(project, jreHome);
  }

  private static Sdk createProjectJdk(@NotNull final Project project) throws CantRunException {
    final Sdk jdk = PathUtilEx.getAnyJdk(project);
    if (jdk == null) {
      throw CantRunException.noJdkConfigured();
    }
    return jdk;
  }

  private static Sdk createAlternativeJdk(@NotNull Project project, @NotNull String jreHome) throws CantRunException {
    final Sdk configuredJdk = ProjectJdkTable.getInstance().findJdk(jreHome);
    if (configuredJdk != null) {
      return configuredJdk;
    }

    if (JdkUtil.checkForJre(jreHome)) {
      final JavaSdk javaSdk = JavaSdk.getInstance();
      return javaSdk.createJdk(ObjectUtils.notNull(javaSdk.getVersionString(jreHome), ""), jreHome);
    }

    UnknownAlternativeSdkResolver.getInstance(project).notifyUserToResolveJreAndFail(jreHome);
    throw new IllegalStateException();
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

  @NotNull
  public static Predicate<Field> getFilter(@NotNull CommonJavaRunConfigurationParameters parameters) {
    return field -> {
      String name = field.getName();
      if ((name.equals("ALTERNATIVE_JRE_PATH_ENABLED") && !parameters.isAlternativeJrePathEnabled()) ||
          (name.equals("ALTERNATIVE_JRE_PATH") && StringUtil.isEmpty(parameters.getAlternativeJrePath()))) {
        return false;
      }
      return true;
    };
  }

  public static void putDependenciesOnModulePath(JavaParameters javaParameters,
                                                 PsiJavaModule module,
                                                 boolean includeTests) {
    Project project = module.getProject();

    Set<PsiJavaModule> explicitModules = new LinkedHashSet<>();

    explicitModules.add(module);
    collectExplicitlyAddedModules(project, javaParameters, explicitModules);

    Set<PsiJavaModule> forModulePath = new HashSet<>(explicitModules);
    for (PsiJavaModule explicitModule : explicitModules) {
      forModulePath.addAll(JavaModuleGraphUtil.getAllDependencies(explicitModule));
    }

    if (!includeTests) {
      putProvidersOnModulePath(project, forModulePath, forModulePath);
    }

    JarFileSystem jarFS = JarFileSystem.getInstance();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

    PathsList classPath = javaParameters.getClassPath();
    PathsList modulePath = javaParameters.getModulePath();

    forModulePath.stream()
      .map(javaModule -> PsiJavaModule.JAVA_BASE.equals(javaModule.getName()) 
                         ? null 
                         : getClasspathEntry(javaModule, fileIndex, jarFS))
      .filter(Objects::nonNull)
      .forEach(file -> putOnModulePath(modulePath, classPath, file));

    VirtualFile productionOutput = getClasspathEntry(module, fileIndex, jarFS);
    if (productionOutput != null) {
      putOnModulePath(modulePath, classPath, productionOutput);
    }
  }

  private static void collectExplicitlyAddedModules(Project project,
                                                    JavaParameters javaParameters,
                                                    Set<PsiJavaModule> explicitModules) {
    ParametersList parametersList = javaParameters.getVMParametersList();
    List<String> parameters = parametersList.getParameters();
    int additionalModulesIdx = parameters.indexOf("--add-modules") + 1;
    String addedModules =
      additionalModulesIdx > 0 && additionalModulesIdx < parameters.size() ? parameters.get(additionalModulesIdx) : null;
    if (addedModules != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      for (String additionalModule : addedModules.split(",")) {
        ContainerUtil.addIfNotNull(explicitModules, psiFacade.findModule(additionalModule.trim(), GlobalSearchScope.allScope(project)));
      }
    }
  }

  private static void putProvidersOnModulePath(Project project, Set<PsiJavaModule> initialModules, Set<PsiJavaModule> forModulePath) {
    Set<String> interfaces = new HashSet<>();
    for (PsiJavaModule explicitModule : initialModules) {
      for (PsiUsesStatement use : explicitModule.getUses()) {
        PsiClassType useClassType = use.getClassType();
        if (useClassType != null) {
          interfaces.add(useClassType.getCanonicalText());
        }
      }
    }

    if (interfaces.isEmpty()) return;

    Set<PsiJavaModule> added = new HashSet<>();
    Consumer<PsiJavaModule> registerProviders = javaModule -> {
      if (forModulePath.add(javaModule)) {
        added.add(javaModule);
      }
    };
    JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
    for (String key : index.getAllKeys(project)) {
      nextModule: 
      for (PsiJavaModule aModule : index.get(key, project, GlobalSearchScope.allScope(project))) {
        if (forModulePath.contains(aModule)) continue;
        for (PsiProvidesStatement provide : aModule.getProvides()) {
          PsiClassType provideInterfaceType = provide.getInterfaceType();
          if (provideInterfaceType != null && interfaces.contains(provideInterfaceType.getCanonicalText())) {
            registerProviders.accept(aModule);
            JavaModuleGraphUtil.getAllDependencies(aModule).forEach(registerProviders);
            continue nextModule;
          }
        }
      }
    }
    if (!added.isEmpty()) {
      putProvidersOnModulePath(project, added, forModulePath);
    }
  }

  private static void putOnModulePath(PathsList modulePath, PathsList classPath, VirtualFile virtualFile) {
    String path = PathUtil.getLocalPath(virtualFile.getPath());
    if (classPath.getPathList().contains(path)) {
      classPath.remove(path);
      modulePath.add(path);
    }
  }

  private static VirtualFile getClasspathEntry(PsiJavaModule javaModule,
                                               ProjectFileIndex fileIndex,
                                               JarFileSystem jarFileSystem) {
    VirtualFile moduleFile = PsiImplUtil.getModuleVirtualFile(javaModule);

    Module moduleDependency = fileIndex.getModuleForFile(moduleFile);
    if (moduleDependency == null) {
      return jarFileSystem.getLocalVirtualFileFor(moduleFile);
    }

    CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(moduleDependency);
    if (moduleExtension != null) {
      return fileIndex.isInTestSourceContent(moduleFile) ? moduleExtension.getCompilerOutputPathForTests()
                                                         : moduleExtension.getCompilerOutputPath();
    }
    return null;
  }
}