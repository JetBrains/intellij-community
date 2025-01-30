// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.EnvFilesOptions;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.configurations.SimpleProgramParameters;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.statistics.MacroUsageCollector;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.macro.MacroWithParams;
import com.intellij.ide.macro.PromptingMacro;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
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
import org.jetbrains.annotations.*;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;

import static com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles;

public class ProgramParametersConfigurator {
  private static final ExtensionPointName<WorkingDirectoryProvider> WORKING_DIRECTORY_PROVIDER_EP_NAME =
    ExtensionPointName.create("com.intellij.module.workingDirectoryProvider");

  /** @deprecated use {@link PathMacroUtil#MODULE_WORKING_DIR} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final String MODULE_WORKING_DIR = "%MODULE_WORKING_DIR%";
  private static final DataKey<Boolean> VALIDATION_MODE = DataKey.create("validation.mode");
  private boolean myValidation;

  public void configureConfiguration(@NotNull SimpleProgramParameters parameters, @NotNull CommonProgramRunConfigurationParameters configuration) {
    Project project = configuration.getProject();
    Module module = getModule(configuration);

    Map<String, String> envs = new HashMap<>();
    if (configuration instanceof EnvFilesOptions) {
      envs.putAll(configureEnvsFromFiles((EnvFilesOptions)configuration, true));
    }
    envs.putAll(configuration.getEnvs());
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

  /**
   * Expands macros, which may contain values representing paths, e.g., working directory, input file, etc.
   */
  @Contract("!null, _, _ -> !null")
  public @Nullable String expandPathAndMacros(String s, @Nullable Module module, @NotNull Project project) {
    String path = s;
    if (path != null) path = expandPath(path, module, project);
    if (path != null) path = expandMacros(path, projectContext(project, module, myValidation), false);
    return path;
  }

  public void setValidation(boolean validation) {
    myValidation = validation;
  }

  @ApiStatus.Internal
  public static @NotNull DataContext projectContext(@NotNull Project project, @Nullable Module module, @Nullable Boolean validationMode) {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, project.getBaseDir())
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY, project.getBaseDir())
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(LangDataKeys.MODULE_CONTEXT, module)
      .add(VALIDATION_MODE, validationMode)
      .build();
  }

  /**
   * Expands macros not representing a path or a parameter string.
   * IMPORTANT: If any macro contains a path or a parameter, consider using one of:
   * <ul>
   * <li>{@link #expandMacrosAndParseParameters} for values representing parameters: program arguments, VM options, etc.
   * <li>{@link #expandPathAndMacros} for paths: working directory, input file, etc.
   * </ul>
   */
  public static String expandMacros(@Nullable String path) {
    return !StringUtil.isEmpty(path) ? expandMacros(path, DataContext.EMPTY_CONTEXT, false) : path;
  }

  /**
   * Expands macros, which may contain values representing parameters, e.g., program arguments, VM options, etc.
   */
  public static @NotNull List<String> expandMacrosAndParseParameters(@Nullable String parametersStringWithMacros) {
    if (StringUtil.isEmpty(parametersStringWithMacros)) {
      return Collections.emptyList();
    }
    String expandedParametersString = expandMacros(parametersStringWithMacros, createContext(DataContext.EMPTY_CONTEXT), true);
    return ParametersListUtil.parse(expandedParametersString);
  }

  private static String expandMacros(@NotNull String path, @NotNull DataContext fallbackDataContext, boolean applyParameterEscaping) {
    if (!Registry.is("allow.macros.for.run.configurations")) {
      return path;
    }

    DataContext context = createContext(fallbackDataContext);
    try {
      Collection<Macro> macros = MacroManager.getInstance().getMacros();
      return MacroManager.expandMacros(path, macros, (macro, occurence) -> {
        String value = StringUtil.notNullize(previewOrExpandMacro(macro, context, occurence));
        return applyParameterEscaping ? ParametersListUtil.escape(value) : value;
      });
    }
    catch (Macro.ExecutionCancelledException ignore) {
      return path; // won't happen :)
    }
  }

  private static DataContext createContext(@NotNull DataContext fallbackDataContext) {
    DataContext envContext = ExecutionManagerImpl.getEnvironmentDataContext();
    return envContext == null ? fallbackDataContext : dataId -> {
      Object data = envContext.getData(dataId);
      return data != null ? data : fallbackDataContext.getData(dataId);
    };
  }

  private static @Nullable String previewOrExpandMacro(Macro macro, DataContext dataContext, String occurence) {
    try {
      if (macro instanceof PromptingMacro || macro instanceof MacroWithParams) {
        Boolean mode = VALIDATION_MODE.getData(dataContext);
        if (mode != null && mode) {
          throw new IncorrectOperationException();
        }
      }
      String value = macro instanceof PromptingMacro ?
                     macro.expandOccurence(dataContext, occurence) :
                     ReadAction.nonBlocking(() -> macro.expandOccurence(dataContext, occurence)).executeSynchronously();
      MacroUsageCollector.logMacroExpanded(macro, value != null);
      return value;
    }
    catch (Macro.ExecutionCancelledException e) {
      return null;
    }
  }

  public @SystemIndependent @Nullable String getWorkingDir(@NotNull CommonProgramRunConfigurationParameters configuration,
                                                           @NotNull Project project,
                                                           @Nullable Module module) {
    String workingDirectory = PathUtil.toSystemIndependentName(configuration.getWorkingDirectory());

    String projectDirectory = getDefaultWorkingDir(project);
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = projectDirectory;
      if (workingDirectory == null) return null;
    }

    workingDirectory = expandPathAndMacros(workingDirectory, module, project)
      .replace(PathMacroUtil.DEPRECATED_MODULE_DIR, PathMacroUtil.MODULE_WORKING_DIR)
      .replace(MODULE_WORKING_DIR, PathMacroUtil.MODULE_WORKING_DIR);

    if (workingDirectory.contains(PathMacroUtil.MODULE_WORKING_DIR)) {
      if (module != null) {
        String moduleDirectory = getDefaultWorkingDir(module);
        if (moduleDirectory != null) return workingDirectory.replace(PathMacroUtil.MODULE_WORKING_DIR, moduleDirectory);
      }
      if (projectDirectory != null) return workingDirectory.replace(PathMacroUtil.MODULE_WORKING_DIR, projectDirectory);
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

  public static final class ParametersConfiguratorException extends RuntimeException {
    public ParametersConfiguratorException(@Nls String message, Throwable cause) {
      super(message, cause);
    }
  }
}