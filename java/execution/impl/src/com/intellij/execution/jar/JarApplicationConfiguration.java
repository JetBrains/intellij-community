// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jar;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.java.JavaLanguageRuntimeType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class JarApplicationConfiguration extends LocatableConfigurationBase implements CommonJavaRunConfigurationParameters,
                                                                                       SearchScopeProvidingRunProfile,
                                                                                       InputRedirectAware,
                                                                                       TargetEnvironmentAwareRunProfile {
  private JarApplicationConfigurationBean myBean = new JarApplicationConfigurationBean();
  private Map<String, String> myEnvs = new LinkedHashMap<>();
  private JavaRunConfigurationModule myConfigurationModule;
  private InputRedirectAware.InputRedirectOptionsImpl myInputRedirectOptions = new InputRedirectOptionsImpl();

  @NotNull
  @Override
  public InputRedirectOptions getInputRedirectOptions() {
    return myInputRedirectOptions;
  }

  public JarApplicationConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    myConfigurationModule = new JavaRunConfigurationModule(project, true);
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<JarApplicationConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new JarApplicationConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    XmlSerializer.deserializeInto(element, myBean);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
    myConfigurationModule.readExternal(element);
    myInputRedirectOptions.readExternal(element);
  }

  @Override
  public RunConfiguration clone() {
    JarApplicationConfiguration clone = (JarApplicationConfiguration)super.clone();
    clone.myEnvs = new LinkedHashMap<>(myEnvs);
    clone.myConfigurationModule = new JavaRunConfigurationModule(getProject(), true);
    clone.myConfigurationModule.setModule(myConfigurationModule.getModule());
    clone.myBean = XmlSerializerUtil.createCopy(myBean);
    clone.myInputRedirectOptions = myInputRedirectOptions.copy();
    return clone;
  }

  public void setModule(Module module) {
    myConfigurationModule.setModule(module);
  }

  public Module getModule() {
    return myConfigurationModule.getModule();
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    XmlSerializer.serializeObjectInto(myBean, element);
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
    if (myConfigurationModule.getModule() != null) {
      myConfigurationModule.writeExternal(element);
    }
    myInputRedirectOptions.writeExternal(element);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), null);
    File jarFile = new File(getJarPath());
    if (!jarFile.exists()) {
      throw new RuntimeConfigurationWarning(JavaCompilerBundle.message("dialog.message.jar.file.doesn.t.exist", jarFile.getAbsolutePath()));
    }
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  public Module @NotNull [] getModules() {
    Module module = myConfigurationModule.getModule();
    return module != null ? new Module[]{module} : Module.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public GlobalSearchScope getSearchScope() {
    return GlobalSearchScopes.executionScope(Arrays.asList(getModules()));
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return new JarApplicationCommandLineState(this, environment);
  }

  public String getJarPath() {
    return myBean.JAR_PATH;
  }

  public void setJarPath(String jarPath) {
    myBean.JAR_PATH = jarPath;
  }

  @Override
  public void setVMParameters(@Nullable String value) {
    myBean.VM_PARAMETERS = value;
  }

  @Override
  public String getVMParameters() {
    return myBean.VM_PARAMETERS;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return myBean.ALTERNATIVE_JRE_PATH_ENABLED;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    myBean.ALTERNATIVE_JRE_PATH_ENABLED = enabled;
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    return myBean.ALTERNATIVE_JRE_PATH;
  }

  @Override
  public void setAlternativeJrePath(String path) {
    myBean.ALTERNATIVE_JRE_PATH = path;
  }

  @Nullable
  @Override
  public String getRunClass() {
    return null;
  }

  @Nullable
  @Override
  public String getPackage() {
    return null;
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    myBean.PROGRAM_PARAMETERS = value;
  }

  @Nullable
  @Override
  public String getProgramParameters() {
    return myBean.PROGRAM_PARAMETERS;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    myBean.WORKING_DIRECTORY = value;
  }

  @Nullable
  @Override
  public String getWorkingDirectory() {
    return myBean.WORKING_DIRECTORY;
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    myEnvs.clear();
    myEnvs.putAll(envs);
  }

  @NotNull
  @Override
  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    myBean.PASS_PARENT_ENVS = passParentEnvs;
  }

  @Override
  public boolean isPassParentEnvs() {
    return myBean.PASS_PARENT_ENVS;
  }

  @Override
  public void onNewConfigurationCreated() {
    super.onNewConfigurationCreated();

    if (StringUtil.isEmpty(getWorkingDirectory())) {
      String baseDir = FileUtil.toSystemIndependentName(StringUtil.notNullize(getProject().getBasePath()));
      setWorkingDirectory(baseDir);
    }
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

  private static class JarApplicationConfigurationBean {
    public String JAR_PATH = "";
    public String VM_PARAMETERS = "";
    public String PROGRAM_PARAMETERS = "";
    public String WORKING_DIRECTORY = "";
    public boolean ALTERNATIVE_JRE_PATH_ENABLED;
    public String ALTERNATIVE_JRE_PATH = "";
    public boolean PASS_PARENT_ENVS = true;
  }
}
