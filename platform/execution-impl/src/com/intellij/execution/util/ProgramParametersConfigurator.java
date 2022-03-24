// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.ide.macro.*;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
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
  private static final DataKey<Boolean> VALIDATION_MODE = DataKey.create("validation.mode");
  private boolean myValidation;

  public void configureConfiguration(@NotNull SimpleProgramParameters parameters, @NotNull CommonProgramRunConfigurationParameters configuration) {
    Project project = configuration.getProject();
    Module module = getModule(configuration);

    Map<String, String> envs = new HashMap<>(configuration.getEnvs());
    EnvironmentUtil.inlineParentOccurrences(envs);
    for (Map.Entry<String, String> each : envs.entrySet()) {
      each.setValue(expandPath(each.getValue(), module, project));
    }
    parameters.setEnv(envs);

    parameters.getProgramParametersList().patchMacroWithEnvs(envs);

    String parametersString = expandPathAndMacros(configuration.getProgramParameters(), module, project);
    parameters.getProgramParametersList().addParametersString(parametersString);

    parameters.setWorkingDirectory(getWorkingDir(configuration, project, module));
    parameters.setPassParentEnvs(configuration.isPassParentEnvs());
  }

  @Contract("!null, _, _ -> !null")
  public @Nullable String expandPathAndMacros(String s, @Nullable Module module, @NotNull Project project) {
    String path = s;
    if (path != null) path = expandPath(path, module, project);
    if (path != null) path = expandMacros(path, projectContext(project, module), false);
    return path;
  }

  public void setValidation(boolean validation) {
    myValidation = validation;
  }

  private DataContext projectContext(Project project, Module module) {
    return dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) return project;
      if (PlatformCoreDataKeys.MODULE.is(dataId) || LangDataKeys.MODULE_CONTEXT.is(dataId)) return module;
      if (VALIDATION_MODE.is(dataId)) return myValidation;
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
      boolean paramsMacro = macro instanceof MacroWithParams;
      String template = "$" + macro.getName() + (paramsMacro ? "(" : "$");
      for (int index = path.indexOf(template);
           index != -1 && index < path.length() + template.length();
           index = path.indexOf(template, index)) {
        String value;
        int tailIndex;
        if (paramsMacro) {
          int endIndex = path.indexOf(")$", index + template.length());
          if (endIndex != -1) {
            value = StringUtil.notNullize(previewOrExpandMacro(macro, dataContext, path.substring(index + template.length(), endIndex)));
            tailIndex = endIndex + 2;
          }
          else {
            //noinspection AssignmentToForLoopParameter
            index += template.length();
            continue;
          }
        }
        else {
          tailIndex = index + template.length();
          value = StringUtil.notNullize(previewOrExpandMacro(macro, dataContext));
        }
        if (applyParameterEscaping) {
          value = ParametersListUtil.escape(value);
        }
        path = path.substring(0, index) + value + path.substring(tailIndex);
        //noinspection AssignmentToForLoopParameter
        index += value.length();
      }
    }
    return path;
  }

  private static @Nullable String previewOrExpandMacro(Macro macro, DataContext dataContext, String @NotNull ... args) {
    try {
      if (macro instanceof PromptingMacro || macro instanceof MacroWithParams) {
        Boolean mode = VALIDATION_MODE.getData(dataContext);
        if (mode != null && mode) {
          throw new IncorrectOperationException();
        }
        return macro.expand(dataContext, args);
      }
      return macro.preview();
    }
    catch (Macro.ExecutionCancelledException e) {
      return null;
    }
  }

  @SystemIndependent
  public @Nullable String getWorkingDir(@NotNull CommonProgramRunConfigurationParameters configuration,
                                        @NotNull Project project,
                                        @Nullable Module module) {
    String workingDirectory = PathUtil.toSystemIndependentName(configuration.getWorkingDirectory());

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
    boolean exists;
    try {
      exists = Files.exists(Paths.get(workingDir));
    }
    catch (InvalidPathException e) {
      exists = false;
    }
    if (!exists) {
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