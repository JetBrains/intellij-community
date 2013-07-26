/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.ProjectTopics;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ModuleRunConfigurationManagerTest extends LightPlatformTestCase {

  private ModuleRunConfigurationManager myManager;
  private final Mockery context = new Mockery();
  private Module myModule;
  private Collection<? extends RunnerAndConfigurationSettings> myConfigurations;
  private final List<RunnerAndConfigurationSettings> myRemovedSettings = ContainerUtil.newArrayList();
  private RunnerAndConfigurationSettings mySettings;
  private final List<RunnerAndConfigurationSettings> myAddedElements = ContainerUtil.newArrayList();

  @Override
  public void setUp() throws Exception {
    PlatformTestCase.initPlatformLangPrefix();
    super.setUp();
    myModule = context.mock(Module.class, "myModule");
    context.checking(new Expectations() {{
      allowing(myModule).getName(); will(returnValue("my-module"));
    }});
    myManager = new ModuleRunConfigurationManager(myModule, new MyRunManagerImpl());

    mySettings = createSettings("my-module-run", new MyModuleBasedConfiguration("my-module-run-config", getProject(), myModule));
    final List<? extends RunnerAndConfigurationSettings> configs = ContainerUtil.newArrayList(
      createSettings("other-run", context.mock(RunConfiguration.class, "other-run-run-config")),
      createSettings("other-module-run", new MyModuleBasedConfiguration("other-module-run-config", getProject(), getModule())),
      mySettings
    );
    myConfigurations = Collections.unmodifiableCollection(configs);
  }

  @NotNull
  private RunnerAndConfigurationSettings createSettings(@NotNull final String name, @NotNull final RunConfiguration runConfiguration) {
    final RunnerAndConfigurationSettings settings = context.mock(RunnerAndConfigurationSettings.class, name);
    context.checking(new Expectations() {{
      allowing(settings).getConfiguration(); will(returnValue(runConfiguration));
    }});
    return settings;
  }

  @Override
  public void tearDown() throws Exception {
    myManager = null;
    super.tearDown();
  }

  public void testInitComponentSubscribesForModulesTopic() throws Exception {
    context.checking(new Expectations() {{
      final MessageBus messageBus = context.mock(MessageBus.class, "messageBus");
      final MessageBusConnection messageBusConnection = context.mock(MessageBusConnection.class, "messageBusConnection");
      oneOf(myModule).getMessageBus();
      will(returnValue(messageBus));
      oneOf(messageBus).connect(myModule); will(returnValue(messageBusConnection));
      oneOf(messageBusConnection).subscribe(ProjectTopics.MODULES, myManager);
    }});
    myManager.initComponent();
    context.assertIsSatisfied();
  }

  public void testGetState() throws Exception {
    myAddedElements.clear();
    myManager.getState();
    assertSameElements("One config should be added to state", myAddedElements, Collections.singleton(mySettings));
  }

  public void testBeforeOtherModuleRemoved() throws Exception {
    myRemovedSettings.clear();
    myManager.beforeModuleRemoved(getProject(), getModule());
    assertEmpty("No settings should be removed", myRemovedSettings);
  }

  public void testBeforeMyModuleRemoved() throws Exception {
    myRemovedSettings.clear();
    myManager.beforeModuleRemoved(getProject(), myModule);
    assertSameElements("one run config should be removed", myRemovedSettings, Collections.singleton(mySettings));
  }

  private static final class MyRunConfigurationModule extends RunConfigurationModule {
    private final Module myModule;
    public MyRunConfigurationModule(@NotNull final Project project, @NotNull final Module module) {
      super(project);
      setModule(module);
      myModule = module;
    }

    @Nullable
    @Override
    public Module getModule() {
      return myModule;
    }
  }

  private final class MyRunManagerImpl extends RunManagerImpl {
    public MyRunManagerImpl() {
      super(LightPlatformTestCase.getProject(), PropertiesComponent.getInstance());
    }

    @NotNull
    @Override
    Collection<? extends RunnerAndConfigurationSettings> getConfigurationSettings() {
      return myConfigurations;
    }

    @Override
    public void removeConfiguration(@Nullable RunnerAndConfigurationSettings settings) {
      myRemovedSettings.add(settings);
    }

    @Override
    void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettings template) throws WriteExternalException {
      myAddedElements.add(template);
    }
  }

  private final class MyModuleBasedConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> {
    public MyModuleBasedConfiguration(@NotNull final String name, @NotNull final Project project, @NotNull final Module module) {
      super(name, new MyRunConfigurationModule(project, module), new MockConfigurationFactory());
    }

    @Override
    public Collection<Module> getValidModules() {
      return null;
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return null;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      return null;
    }
  }

  private static class MockConfigurationFactory extends ConfigurationFactory {
    public MockConfigurationFactory() {
      super(new MyConfigurationType());
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      throw new UnsupportedOperationException("Not Implemented");
    }

  }

  private static class MyConfigurationType implements ConfigurationType {
    @Override
    public String getDisplayName() {
      return "mock";
    }

    @Override
    public String getConfigurationTypeDescription() {
      return "mock type";
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    @NotNull
    public String getId() {
      return "MockRuntimeConfiguration";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[0];
    }
  }
}
