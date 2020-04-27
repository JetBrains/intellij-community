// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
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
  private static final ExtensionPointName<WorkingDirectoryProvider> WORKING_DIRECTORY_PROVIDER_EP_NAME= ExtensionPointName
    .create("com.intellij.module.workingDirectoryProvider");
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final String MODULE_WORKING_DIR = "%MODULE_WORKING_DIR%";

  public void configureConfiguration(SimpleProgramParameters parameters, CommonProgramRunConfigurationParameters configuration) {
    Project project = configuration.getProject();
    Module module = getModule(configuration);

    final String parametersString = expandPathAndMacros(configuration.getProgramParameters(), module, project);
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
  public @Nullable String expandPathAndMacros(String s, Module module, Project project) {
    final String path = expandPath(s, module, project);
    if (path == null) return null;
    return expandMacros(path, projectContext(project, module), false);
  }

  private static @NotNull DataContext projectContext(Project project, Module module) {
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
    if (StringUtil.isEmpty(path)) {
      return path;
    }
    return expandMacros(path, DataContext.EMPTY_CONTEXT, false);
  }

  public static @NotNull List<String> expandMacrosAndParseParameters(@Nullable String parametersStringWithMacros) {
    if (StringUtil.isEmpty(parametersStringWithMacros)) {
      return Collections.emptyList();
    }
    final String expandedParametersString = expandMacros(parametersStringWithMacros, DataContext.EMPTY_CONTEXT, true);
    return ParametersListUtil.parse(expandedParametersString);
  }

  private static @NotNull String expandMacros(@NotNull String path, @NotNull DataContext dataContext, boolean applyParameterEscaping) {
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

  private static @Nullable String previewOrExpandMacro(@NotNull Macro macro,
                                                       @NotNull DataContext dataContext) {
    try {
      return macro instanceof PromptingMacro
             ? ((PromptingMacro)macro).expand(dataContext)
             : macro.preview();
    }
    catch (Macro.ExecutionCancelledException e) {
      return null;
    }
  }

  @Nullable
  public String getWorkingDir(CommonProgramRunConfigurationParameters configuration, Project project, Module module) {
    String workingDirectory = configuration.getWorkingDirectory();
    String defaultWorkingDir = getDefaultWorkingDir(project);
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = defaultWorkingDir;
      if (workingDirectory == null) {
        return null;
      }
    }
    workingDirectory = expandPathAndMacros(workingDirectory, module, project);
    if (!FileUtil.isAbsolutePlatformIndependent(workingDirectory) && defaultWorkingDir != null) {
      if (PathMacroUtil.DEPRECATED_MODULE_DIR.equals(workingDirectory)) {
        return defaultWorkingDir;
      }

      //noinspection deprecation
      if (MODULE_WORKING_DIR.equals(workingDirectory)) {
        workingDirectory = PathMacroUtil.MODULE_WORKING_DIR;
      }

      if (PathMacroUtil.MODULE_WORKING_DIR.equals(workingDirectory)) {
        if (module == null) {
          return defaultWorkingDir;
        }
        else {
          String workingDir = getDefaultWorkingDir(module);
          if (workingDir != null) {
            return workingDir;
          }
        }
      }
      workingDirectory = defaultWorkingDir + "/" + workingDirectory;
    }
    return workingDirectory;
  }

  @Nullable
  protected String getDefaultWorkingDir(@NotNull Project project) {
    return PathUtil.getLocalPath(project.getBaseDir());
  }

  @Nullable
  protected String getDefaultWorkingDir(@NotNull Module module) {
    for (WorkingDirectoryProvider provider : WORKING_DIRECTORY_PROVIDER_EP_NAME.getExtensions()) {
      @SystemIndependent String path = provider.getWorkingDirectoryPath(module);
      if (path != null) return path;
    }
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return PathUtil.getLocalPath(roots[0]);
    }
    return null;
  }

  public void checkWorkingDirectoryExist(CommonProgramRunConfigurationParameters configuration, Project project, Module module)
    throws RuntimeConfigurationWarning {
    final String workingDir = getWorkingDir(configuration, project, module);
    if (workingDir == null) {
      throw new RuntimeConfigurationWarning("Working directory is null for "+
                                            "project '" + project.getName() + "' ("+project.getBasePath()+")"
                                            + ", module " + (module == null? "null" : "'" + module.getName() + "' (" + module.getModuleFilePath() + ")"));
    }
    if (!new File(workingDir).exists()) {
      throw new RuntimeConfigurationWarning("Working directory '" + workingDir + "' doesn't exist");
    }
  }

  protected String expandPath(@Nullable String path, Module module, Project project) {
    // https://youtrack.jetbrains.com/issue/IDEA-190100
    // if old macro is used (because stored in the default project and applied for a new imported project) and module file stored under .idea, use new module macro instead
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

  @Nullable
  protected Module getModule(CommonProgramRunConfigurationParameters configuration) {
    if (configuration instanceof ModuleBasedConfiguration) {
      return ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
    }
    return null;
  }
}
