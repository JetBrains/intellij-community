/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.PathsList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class ApplicationConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, ConfigurationWithCommandLineShortener, SingleClassConfiguration, RefactoringListenerProvider {

  /* deprecated, but 3rd-party used variables */
  @Deprecated public String MAIN_CLASS_NAME;
  @Deprecated public String PROGRAM_PARAMETERS;
  @Deprecated public String WORKING_DIRECTORY;
  @Deprecated public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  @Deprecated public String ALTERNATIVE_JRE_PATH;
  @Deprecated public boolean ENABLE_SWING_INSPECTOR;
  /* */

  private ShortenCommandLine myShortenCommandLine = null;

  public ApplicationConfiguration(final String name, final Project project, ApplicationConfigurationType applicationConfigurationType) {
    this(name, project, applicationConfigurationType.getConfigurationFactories()[0]);
  }

  protected ApplicationConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  /**
   * Because we have to keep backward compatibility, never use `getOptions()` to get or set values - use only designated getters/setters.
   */
  @Override
  protected ApplicationConfigurationOptions getOptions() {
    return (ApplicationConfigurationOptions)super.getOptions();
  }

  @Override
  protected Class<? extends ModuleBasedConfigurationOptions> getOptionsClass() {
    return ApplicationConfigurationOptions.class;
  }

  @Override
  public void setMainClass(@NotNull PsiClass psiClass) {
    final Module originalModule = getConfigurationModule().getModule();
    setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(psiClass));
    setModule(JavaExecutionUtil.findModule(psiClass));
    restoreOriginalModule(originalModule);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLineState state = new JavaApplicationCommandLineState<>(this, env);
    JavaRunConfigurationModule module = getConfigurationModule();
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject(), module.getSearchScope()));
    return state;
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<ApplicationConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new ApplicationConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    final RefactoringElementListener listener = RefactoringListeners.
      getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
    return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
  }

  @Override
  @Nullable
  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(getMainClassName());
  }

  @Nullable
  public String getMainClassName() {
    //noinspection deprecation
    return MAIN_CLASS_NAME;
  }

  @Override
  @Nullable
  public String suggestedName() {
    if (getMainClassName() == null) {
      return null;
    }
    return JavaExecutionUtil.getPresentableClassName(getMainClassName());
  }

  @Override
  public String getActionName() {
    if (getMainClassName() == null) {
      return null;
    }
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(getMainClassName()), 6) + ".main()";
  }

  @Override
  public void setMainClassName(@Nullable String qualifiedName) {
    //noinspection deprecation
    MAIN_CLASS_NAME = qualifiedName;
    getOptions().setMainClassName(qualifiedName);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(getMainClassName(), ExecutionBundle.message("no.main.class.specified.error.text"));
    if (!PsiMethodUtil.hasMainMethod(psiClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", getMainClassName()));
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), configurationModule.getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public void setVMParameters(@Nullable String value) {
    getOptions().setVmParameters(value);
  }

  @Override
  public String getVMParameters() {
    return getOptions().getVmParameters();
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    //noinspection deprecation
    PROGRAM_PARAMETERS = value;
    getOptions().setProgramParameters(value);
  }

  @Override
  public String getProgramParameters() {
    //noinspection deprecation
    return PROGRAM_PARAMETERS;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    String normalizedValue = ExternalizablePath.urlValue(value);
    //noinspection deprecation
    WORKING_DIRECTORY = normalizedValue;
    getOptions().setWorkingDirectory(normalizedValue);
  }

  @Override
  public String getWorkingDirectory() {
    //noinspection deprecation
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  @Override
  public void setPassParentEnvs(boolean value) {
    getOptions().setPassParentEnv(value);
  }

  @Override
  @NotNull
  public Map<String, String> getEnvs() {
    return getOptions().getEnv();
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    getOptions().setEnv(envs);
  }

  @Override
  public boolean isPassParentEnvs() {
    return getOptions().isPassParentEnv();
  }

  @Override
  @Nullable
  public String getRunClass() {
    return getMainClassName();
  }

  @Override
  @Nullable
  public String getPackage() {
    return null;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    //noinspection deprecation
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    //noinspection deprecation
    ALTERNATIVE_JRE_PATH_ENABLED = enabled;
    getOptions().setAlternativeJrePathEnabled(enabled);
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    //noinspection deprecation
    return ALTERNATIVE_JRE_PATH;
  }

  @Override
  public void setAlternativeJrePath(@Nullable String path) {
    //noinspection deprecation
    ALTERNATIVE_JRE_PATH = path;
    getOptions().setAlternativeJrePath(path);
  }

  public boolean isProvidedScopeIncluded() {
    return getOptions().getIncludeProvidedScope();
  }

  public void setIncludeProvidedScope(boolean value) {
    getOptions().setIncludeProvidedScope(value);
  }

  @Override
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), getMainClassName());
  }

  @SuppressWarnings("deprecation")
  @Override
  public void readExternal(@NotNull final Element element) {
    super.readExternal(element);

    ApplicationConfigurationOptions options = getOptions();
    MAIN_CLASS_NAME = options.getMainClassName();
    PROGRAM_PARAMETERS = options.getProgramParameters();
    WORKING_DIRECTORY = options.getWorkingDirectory();
    ALTERNATIVE_JRE_PATH = options.getAlternativeJrePath();
    ALTERNATIVE_JRE_PATH_ENABLED = options.isAlternativeJrePathEnabled();
    ENABLE_SWING_INSPECTOR = options.isSwingInspectorEnabled();

    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    setShortenCommandLine(ShortenCommandLine.readShortenClasspathMethod(element));
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    ShortenCommandLine.writeShortenClasspathMethod(element, myShortenCommandLine);
  }

  @Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return myShortenCommandLine;
  }

  @Override
  public void setShortenCommandLine(ShortenCommandLine mode) {
    myShortenCommandLine = mode;
  }

  public boolean isSwingInspectorEnabled() {
    //noinspection deprecation
    return ENABLE_SWING_INSPECTOR;
  }

  public void setSwingInspectorEnabled(boolean value) {
    //noinspection deprecation
    ENABLE_SWING_INSPECTOR = value;
    getOptions().setSwingInspectorEnabled(value);
  }

  public static class JavaApplicationCommandLineState<T extends ApplicationConfiguration> extends BaseJavaApplicationCommandLineState<T> {
    public JavaApplicationCommandLineState(@NotNull final T configuration, final ExecutionEnvironment environment) {
      super(environment, configuration);
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
      final JavaParameters params = new JavaParameters();
      T configuration = getConfiguration();
      params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

      final JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
      final String jreHome = myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null;
      if (module.getModule() != null) {
        DumbService.getInstance(module.getProject()).runWithAlternativeResolveEnabled(() -> {
          int classPathType = JavaParametersUtil.getClasspathType(module, myConfiguration.getMainClassName(), false, myConfiguration.isProvidedScopeIncluded());
          JavaParametersUtil.configureModule(module, params, classPathType, jreHome);
        });
      }
      else {
        JavaParametersUtil.configureProject(module.getProject(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jreHome);
      }

      params.setMainClass(myConfiguration.getMainClassName());

      setupJavaParameters(params);

      setupModulePath(params, module);

      return params;
    }

    @Override
    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      GeneralCommandLine line = super.createCommandLine();
      Map<String, String> content = line.getUserData(JdkUtil.COMMAND_LINE_CONTENT);
      if (content != null) {
        content.forEach((key, value) -> addConsoleFilters(new ArgumentFileFilter(key, value)));
      }
      return line;
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      OSProcessHandler processHandler = super.startProcess();
      if (processHandler instanceof KillableProcessHandler && DebuggerSettings.getInstance().KILL_PROCESS_IMMEDIATELY) {
        ((KillableProcessHandler)processHandler).setShouldKillProcessSoftly(false);
      }
      return processHandler;
    }

    private static void setupModulePath(JavaParameters params, JavaRunConfigurationModule module) {
      if (JavaSdkUtil.isJdkAtLeast(params.getJdk(), JavaSdkVersion.JDK_1_9)) {
        PsiJavaModule mainModule = DumbService.getInstance(module.getProject()).computeWithAlternativeResolveEnabled(
          () -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.getMainClass())));
        if (mainModule != null) {
          params.setModuleName(mainModule.getName());
          PathsList classPath = params.getClassPath(), modulePath = params.getModulePath();
          modulePath.addAll(classPath.getPathList());
          classPath.clear();
        }
      }
    }
  }
}