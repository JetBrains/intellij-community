// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.statistics.FusAwareRunConfiguration;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentConfigurations;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.java.JavaLanguageRuntimeType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class ApplicationConfiguration extends JavaRunConfigurationBase
  implements SingleClassConfiguration, RefactoringListenerProvider, InputRedirectAware, TargetEnvironmentAwareRunProfile,
             FusAwareRunConfiguration {
  /* deprecated, but 3rd-party used variables */
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated public String MAIN_CLASS_NAME;
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated public String PROGRAM_PARAMETERS;
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated public String WORKING_DIRECTORY;
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated public String ALTERNATIVE_JRE_PATH;
  /* */

  public ApplicationConfiguration(String name, @NotNull Project project, @NotNull ApplicationConfigurationType configurationType) {
    this(name, project, configurationType.getConfigurationFactories()[0]);
  }

  public ApplicationConfiguration(final String name, @NotNull Project project) {
    this(name, project, ApplicationConfigurationType.getInstance().getConfigurationFactories()[0]);
  }

  protected ApplicationConfiguration(String name, @NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  // backward compatibility (if 3rd-party plugin extends ApplicationConfigurationType but uses own factory without options class)
  @Override
  @NotNull
  protected final Class<? extends JvmMainMethodRunConfigurationOptions> getDefaultOptionsClass() {
    return JvmMainMethodRunConfigurationOptions.class;
  }

  /**
   * Because we have to keep backward compatibility, never use `getOptions()` to get or set values - use only designated getters/setters.
   */
  @NotNull
  @Override
  protected JvmMainMethodRunConfigurationOptions getOptions() {
    return (JvmMainMethodRunConfigurationOptions)super.getOptions();
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
    if (Registry.is("ide.new.run.config", true)) {
      return new JavaApplicationSettingsEditor(this);
    }
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

  @NlsSafe
  @Nullable
  public String getMainClassName() {
    return MAIN_CLASS_NAME;
  }

  @Override
  @Nullable
  public String suggestedName() {
    String mainClassName = getMainClassName();
    if (mainClassName == null) {
      return null;
    }
    String configName = JavaExecutionUtil.getPresentableClassName(mainClassName);
    if (configName != null &&
        RunManager.getInstance(getProject()).findConfigurationByTypeAndName(getType(), configName) != null) {
      return mainClassName;
    }
    return configName;
  }

  @Override
  public String getActionName() {
    if (getMainClassName() == null) {
      return null;
    }
    @NlsSafe String mainSuffix = ".main()";
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(getMainClassName()), 6) + mainSuffix;
  }

  @Override
  public void setMainClassName(@Nullable String qualifiedName) {
    MAIN_CLASS_NAME = qualifiedName;
    getOptions().setMainClassName(qualifiedName);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (TargetEnvironmentConfigurations.getEffectiveTargetName(this, getProject()) == null) {
      JavaParametersUtil.checkAlternativeJRE(this);
    }
    final JavaRunConfigurationModule configurationModule = checkClass();
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), configurationModule.getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @NotNull
  public JavaRunConfigurationModule checkClass() throws RuntimeConfigurationException {
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass =
      configurationModule.checkModuleAndClassName(getMainClassName(), ExecutionBundle.message("no.main.class.specified.error.text"));
    if (!PsiMethodUtil.hasMainMethod(psiClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", getMainClassName()));
    }
    return configurationModule;
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
    PROGRAM_PARAMETERS = value;
    getOptions().setProgramParameters(value);
  }

  @Override
  public String getProgramParameters() {
    return PROGRAM_PARAMETERS;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    String normalizedValue = StringUtil.isEmptyOrSpaces(value) ? null : value.trim();
    WORKING_DIRECTORY = PathUtil.toSystemDependentName(normalizedValue);

    String independentValue = PathUtil.toSystemIndependentName(normalizedValue);
    getOptions().setWorkingDirectory(Objects.equals(independentValue, getProject().getBasePath()) ? null : independentValue);
  }

  @Override
  public String getWorkingDirectory() {
    return WORKING_DIRECTORY;
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
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    boolean changed = ALTERNATIVE_JRE_PATH_ENABLED != enabled;
    ALTERNATIVE_JRE_PATH_ENABLED = enabled;
    getOptions().setAlternativeJrePathEnabled(enabled);
    onAlternativeJreChanged(changed, getProject());
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    return ALTERNATIVE_JRE_PATH;
  }

  @Override
  public void setAlternativeJrePath(@Nullable String path) {
    boolean changed = !Objects.equals(ALTERNATIVE_JRE_PATH, path);
    ALTERNATIVE_JRE_PATH = path;
    getOptions().setAlternativeJrePath(path);
    onAlternativeJreChanged(changed, getProject());
  }

  @Override
  public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return target.getRuntimes().findByType(JavaLanguageRuntimeConfiguration.class) != null;
  }

  @Nullable
  @Override
  public LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return LanguageRuntimeType.EXTENSION_NAME.findExtension(JavaLanguageRuntimeType.class);
  }

  @Nullable
  @Override
  public String getDefaultTargetName() {
    return getOptions().getRemoteTarget();
  }

  @Override
  public void setDefaultTargetName(@Nullable String targetName) {
    getOptions().setRemoteTarget(targetName);
  }

  @Override
  public boolean needPrepareTarget() {
    return TargetEnvironmentAwareRunProfile.super.needPrepareTarget() || runsUnderWslJdk();
  }

  @Override
  public @NotNull List<EventPair<?>> getAdditionalUsageData() {
    PsiClass mainClass = getMainClass();
    if (mainClass == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(EventFields.Language.with(mainClass.getLanguage()));
  }

  public static void onAlternativeJreChanged(boolean changed, Project project) {
    if (changed) {
      AlternativeSdkRootsProvider.reindexIfNeeded(project);
    }
  }

  public boolean isProvidedScopeIncluded() {
    return getOptions().isIncludeProvidedScope();
  }

  public void setIncludeProvidedScope(boolean value) {
    getOptions().setIncludeProvidedScope(value);
  }

  @Override
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), getMainClassName());
  }

  @Override
  public void readExternal(@NotNull final Element element) {
    super.readExternal(element);

    syncOldStateFields();

    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
  }

  private void syncOldStateFields() {
    JvmMainMethodRunConfigurationOptions options = getOptions();

    String workingDirectory = options.getWorkingDirectory();
    if (workingDirectory == null) {
      workingDirectory = PathUtil.toSystemDependentName(getProject().getBasePath());
    }
    else {
      workingDirectory = FileUtilRt.toSystemDependentName(VirtualFileManager.extractPath(workingDirectory));
    }

    MAIN_CLASS_NAME = options.getMainClassName();
    PROGRAM_PARAMETERS = options.getProgramParameters();
    WORKING_DIRECTORY = workingDirectory;
    ALTERNATIVE_JRE_PATH = options.getAlternativeJrePath();
    ALTERNATIVE_JRE_PATH_ENABLED = options.isAlternativeJrePathEnabled();
  }

  @Override
  public void setOptionsFromConfigurationFile(@NotNull BaseState state) {
    super.setOptionsFromConfigurationFile(state);
    syncOldStateFields();
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
  }

  @Nullable
  @Override
  public ShortenCommandLine getShortenCommandLine() {
    return getOptions().getShortenClasspath();
  }

  @Override
  public void setShortenCommandLine(@Nullable ShortenCommandLine mode) {
    getOptions().setShortenClasspath(mode);
  }

  @NotNull
  @Override
  public InputRedirectOptions getInputRedirectOptions() {
    return getOptions().getRedirectOptions();
  }

  @Override
  public Module getDefaultModule() {
    PsiClass mainClass = getMainClass();
    if (mainClass != null) {
      Module module = ModuleUtilCore.findModuleForPsiElement(mainClass);
      if (module != null) return module;
    }
    return super.getDefaultModule();
  }

  public static class JavaApplicationCommandLineState<T extends ApplicationConfiguration> extends ApplicationCommandLineState<T> {
    public JavaApplicationCommandLineState(@NotNull final T configuration, final ExecutionEnvironment environment) {
      super(configuration, environment);
    }
    
    @TestOnly
    public JavaParameters createJavaParameters4Test() throws ExecutionException {
      return createJavaParameters();
    }

    @Override
    protected boolean isProvidedScopeIncluded() {
      return myConfiguration.isProvidedScopeIncluded();
    }

    @Override
    protected boolean isReadActionRequired() {
      return false;
    }
  }
}