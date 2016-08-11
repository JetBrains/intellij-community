/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.application;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, SingleClassConfiguration, RefactoringListenerProvider {

  public String MAIN_CLASS_NAME;
  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;
  public String WORKING_DIRECTORY;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;
  public boolean ENABLE_SWING_INSPECTOR;

  public String ENV_VARIABLES;
  private final Map<String,String> myEnvs = new LinkedHashMap<>();
  public boolean PASS_PARENT_ENVS = true;

  public ApplicationConfiguration(final String name, final Project project, ApplicationConfigurationType applicationConfigurationType) {
    this(name, project, applicationConfigurationType.getConfigurationFactories()[0]);
  }

  protected ApplicationConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, true), factory);
  }

  @Override
  public void setMainClass(final PsiClass psiClass) {
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
    return getConfigurationModule().findClass(MAIN_CLASS_NAME);
  }

  @Override
  @Nullable
  public String suggestedName() {
    if (MAIN_CLASS_NAME == null) {
      return null;
    }
    return JavaExecutionUtil.getPresentableClassName(MAIN_CLASS_NAME);
  }

  @Override
  public String getActionName() {
    if (MAIN_CLASS_NAME == null || MAIN_CLASS_NAME.length() == 0) {
      return null;
    }
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(MAIN_CLASS_NAME), 6) + ".main()";
  }

  @Override
  public void setMainClassName(final String qualifiedName) {
    MAIN_CLASS_NAME = qualifiedName;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(MAIN_CLASS_NAME, ExecutionBundle.message("no.main.class.specified.error.text"));
    if (!PsiMethodUtil.hasMainMethod(psiClass)) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", MAIN_CLASS_NAME));
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), configurationModule.getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public void setVMParameters(String value) {
    VM_PARAMETERS = value;
  }

  @Override
  public String getVMParameters() {
    return VM_PARAMETERS;
  }

  @Override
  public void setProgramParameters(String value) {
    PROGRAM_PARAMETERS = value;
  }

  @Override
  public String getProgramParameters() {
    return PROGRAM_PARAMETERS;
  }

  @Override
  public void setWorkingDirectory(String value) {
    WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
  }

  @Override
  public String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    PASS_PARENT_ENVS = passParentEnvs;
  }

  @Override
  @NotNull
  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  @Override
  public void setEnvs(@NotNull final Map<String, String> envs) {
    myEnvs.clear();
    myEnvs.putAll(envs);
  }

  @Override
  public boolean isPassParentEnvs() {
    return PASS_PARENT_ENVS;
  }

  @Override
  @Nullable
  public String getRunClass() {
    return MAIN_CLASS_NAME;
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
     ALTERNATIVE_JRE_PATH_ENABLED = enabled;
   }

   @Nullable
   @Override
   public String getAlternativeJrePath() {
     return ALTERNATIVE_JRE_PATH;
   }

   @Override
   public void setAlternativeJrePath(String path) {
     ALTERNATIVE_JRE_PATH = path;
   }

  @Override
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), MAIN_CLASS_NAME);
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(this, element);
    readModule(element);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeModule(element);
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
  }

  public static class JavaApplicationCommandLineState<T extends ApplicationConfiguration> extends BaseJavaApplicationCommandLineState<T> {
    public JavaApplicationCommandLineState(@NotNull final T configuration, final ExecutionEnvironment environment) {
      super(environment, configuration);
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
      final JavaParameters params = new JavaParameters();
      params.setUseClasspathJar(true);
      final JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
      final String jreHome = myConfiguration.ALTERNATIVE_JRE_PATH_ENABLED ? myConfiguration.ALTERNATIVE_JRE_PATH : null;

      if (module.getModule() != null) {
        final int classPathType = JavaParametersUtil.getClasspathType(module, myConfiguration.MAIN_CLASS_NAME, false);
        JavaParametersUtil.configureModule(module, params, classPathType, jreHome);
      }
      else {
        JavaParametersUtil.configureProject(module.getProject(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jreHome);
      }
      params.setMainClass(myConfiguration.MAIN_CLASS_NAME);
      setupJavaParameters(params);

      return params;
    }
  }
}
