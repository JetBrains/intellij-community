// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.macro.PromptingMacro;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.WorkingDirectoryProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProgramParametersConfigurator {
  private static final ExtensionPointName<WorkingDirectoryProvider> WORKING_DIRECTORY_PROVIDER_EP_NAME =
    ExtensionPointName.create("com.intellij.module.workingDirectoryProvider");

  /** @deprecated use {@link PathMacroUtil#MODULE_WORKING_DIR} instead */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final String MODULE_WORKING_DIR = "%MODULE_WORKING_DIR%";

  public void configureConfiguration(@NotNull SimpleProgramParameters parameters, @NotNull CommonProgramRunConfigurationParameters configuration) {
    Project project = configuration.getProject();
    Module module = getModule(configuration);

    String parametersString = expandPathAndMacros(configuration.getProgramParameters(), module, project);
    parameters.getProgramParametersList().addParametersString(parametersString);

    parameters.setWorkingDirectory(getWorkingDir(configuration, project, module));

    Map<String, String> envs = new HashMap<>(configuration.getEnvs());
    EnvironmentUtil.inlineParentOccurrences(envs);
    for (Map.Entry<String, String> each : envs.entrySet()) {
      each.setValue(expandPath(each.getValue(), module, project));
    }

    parameters.setEnv(envs);
    parameters.setPassParentEnvs(configuration.isPassParentEnvs());
  }

  @Contract("!null, _, _ -> !null")
  public @Nullable String expandPathAndMacros(String s, @Nullable Module module, @NotNull Project project) {
    String path = s;
    if (path != null) path = expandPath(path, module, project);
    if (path != null) path = expandMacros(path, projectContext(project, module), false);
    return path;
  }

  private static DataContext projectContext(Project project, Module module) {
    return dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) return project;
      if (LangDataKeys.MODULE.is(dataId) || LangDataKeys.MODULE_CONTEXT.is(dataId)) return module;
      return null;
    };
  }

  /**
   * Unless expanding macros in a generic value that doesn't represent a path of some kind, or a parameter string,
   * consider using the following specialized methods instead:
   *
   * @see #expandMacrosAndParseParameters For values representing parameters: program arguments, VM options
   * @see #expandPathAndMacros For paths: working directory, input file, etc.
   */
  public static String expandMacros(@Nullable String path) {
    return !StringUtil.isEmpty(path) ? expandMacros(path, DataContext.EMPTY_CONTEXT, false) : path;
  }

  public static @NotNull List<String> expandMacrosAndParseParameters(@Nullable String parametersStringWithMacros) {
    if (StringUtil.isEmpty(parametersStringWithMacros)) {
      return Collections.emptyList();
    }
    String expandedParametersString = expandMacros(parametersStringWithMacros, DataContext.EMPTY_CONTEXT, true);
    return ParametersListUtil.parse(expandedParametersString);
  }

  private static String expandMacros(String path, DataContext dataContext, boolean applyParameterEscaping) {
    if (!Registry.is("allow.macros.for.run.configurations")) {
      return path;
    }

    for (Macro macro : MacroManager.getInstance().getMacros()) {
      String template = "$" + macro.getName() + "$";
      for (int index = path.indexOf(template);
           index != -1 && index < path.length() + template.length();
           index = path.indexOf(template, index)) {
        String value = StringUtil.notNullize(previewOrExpandMacro(macro, dataContext));
        if (applyParameterEscaping) {
          value = ParametersListUtil.escape(value);
        }
        path = path.substring(0, index) + value + path.substring(index + template.length());
        //noinspection AssignmentToForLoopParameter
        index += value.length();
      }
    }
    return path;
  }

  private static @Nullable String previewOrExpandMacro(Macro macro, DataContext dataContext) {
    try {
      return macro instanceof PromptingMacro ? macro.expand(dataContext) : macro.preview();
    }
    catch (Macro.ExecutionCancelledException e) {
      return null;
    }
  }

  public @Nullable String getWorkingDir(@NotNull CommonProgramRunConfigurationParameters configuration,
                                        @NotNull Project project,
                                        @Nullable Module module) {
    String workingDirectory = configuration.getWorkingDirectory();

    String projectDirectory = getDefaultWorkingDir(project);
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = projectDirectory;
      if (workingDirectory == null) return null;
    }

    workingDirectory = expandPathAndMacros(workingDirectory, module, project);

    if (MODULE_WORKING_DIR.equals(workingDirectory) || PathMacroUtil.DEPRECATED_MODULE_DIR.equals(workingDirectory)) {
      workingDirectory = PathMacroUtil.MODULE_WORKING_DIR;
    }
    if (PathMacroUtil.MODULE_WORKING_DIR.equals(workingDirectory)) {
      if (module != null) {
        String moduleDirectory = getDefaultWorkingDir(module);
        if (moduleDirectory != null) return moduleDirectory;
      }
      if (projectDirectory != null) return projectDirectory;
    }

    if (projectDirectory != null && !OSAgnosticPathUtil.isAbsolute(workingDirectory)) {
      workingDirectory = projectDirectory + '/' + workingDirectory;
    }

    return workingDirectory;
  }

  protected @Nullable String getDefaultWorkingDir(@NotNull Project project) {
    String path = project.getBasePath();
    return path != null && LocalFileSystem.getInstance().findFileByPath(path) != null ? path : null;
  }

  protected @Nullable String getDefaultWorkingDir(@NotNull Module module) {
    for (WorkingDirectoryProvider provider : WORKING_DIRECTORY_PROVIDER_EP_NAME.getExtensions()) {
      @SystemIndependent String path = provider.getWorkingDirectoryPath(module);
      if (path != null) return path;
    }
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return roots[0].getPath();
    }
    return null;
  }

  public void checkWorkingDirectoryExist(@NotNull CommonProgramRunConfigurationParameters configuration,
                                         @NotNull Project project,
                                         @Nullable Module module) throws RuntimeConfigurationWarning {
    String workingDir = getWorkingDir(configuration, project, module);
    if (workingDir == null) {
      throw new RuntimeConfigurationWarning(
        ExecutionBundle.message("dialog.message.working.directory.null.for.project.module", project.getName(), project.getBasePath(),
                                module == null ? "null" : "'" + module.getName() + "' (" + module.getModuleFilePath() + ")"));
    }
    if (!new File(workingDir).exists()) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("dialog.message.working.directory.doesn.t.exist", workingDir));
    }
  }

  protected String expandPath(@Nullable String path, @Nullable Module module, @NotNull Project project) {
    // https://youtrack.jetbrains.com/issue/IDEA-190100
    // if an old macro is used (because stored in the default project and applied for a new imported project)
    // and module file stored under .idea, use the new module macro instead
    if (module != null && PathMacroUtil.DEPRECATED_MODULE_DIR.equals(path) &&
        module.getModuleFilePath().contains("/" + Project.DIRECTORY_STORE_FOLDER + "/") &&
        ExternalProjectSystemRegistry.getInstance().getExternalSource(module) != null /* not really required but to reduce possible impact */) {
      return getDefaultWorkingDir(module);
    }

    path = PathMacroManager.getInstance(project).expandPath(path);
    if (module != null) {
      path = PathMacroManager.getInstance(module).expandPath(path);
    }
    return path;
  }

  protected @Nullable Module getModule(CommonProgramRunConfigurationParameters cp) {
    return cp instanceof ModuleBasedConfiguration ? ((ModuleBasedConfiguration<?, ?>)cp).getConfigurationModule().getModule() : null;
  }
}