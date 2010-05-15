/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dyoma
 */
public class RunnerAndConfigurationSettingsImpl implements JDOMExternalizable, Cloneable, RunnerAndConfigurationSettings, Comparable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunnerAndConfigurationSettings");

  @NonNls
  private static final String RUNNER_ELEMENT = "RunnerSettings";
  @NonNls
  private static final String CONFIGURATION_ELEMENT = "ConfigurationWrapper";
  @NonNls
  private static final String RUNNER_ID = "RunnerId";
  @NonNls
  private static final String CONFIGURATION_TYPE_ATTRIBUTE = "type";
  @NonNls
  private static final String FACTORY_NAME_ATTRIBUTE = "factoryName";
  @NonNls
  private static final String TEMPLATE_FLAG_ATTRIBUTE = "default";
  @NonNls
  public static final String NAME_ATTR = "name";
  @NonNls
  protected static final String DUMMY_ELEMENT_NANE = "dummy";
  @NonNls
  private static final String TEMPORARY_ATTRIBUTE = "temporary";

  /** for compatibility */
  @NonNls
  private static final String TEMP_CONFIGURATION = "tempConfiguration";

  private final RunManagerImpl myManager;
  private RunConfiguration myConfiguration;
  private boolean myIsTemplate;

  private final Map<ProgramRunner, RunnerSettings> myRunnerSettings = new HashMap<ProgramRunner, RunnerSettings>();
  private List<Element> myUnloadedRunnerSettings = null;

  private final Map<ProgramRunner, ConfigurationPerRunnerSettings> myConfigurationPerRunnerSettings = new HashMap<ProgramRunner, ConfigurationPerRunnerSettings>();
  private List<Element> myUnloadedConfigurationPerRunnerSettings = null;

  private boolean myTemporary;

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager) {
    myManager = manager;
  }

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager, RunConfiguration configuration, boolean isTemplate) {
    myManager = manager;
    myConfiguration = configuration;
    myIsTemplate = isTemplate;
  }

  @Nullable
  public ConfigurationFactory getFactory() {
    return myConfiguration == null ? null : myConfiguration.getFactory();
  }

  public boolean isTemplate() {
    return myIsTemplate;
  }

  public boolean isTemporary() {
    return myTemporary;
  }

  public void setTemporary(boolean temporary) {
    myTemporary = temporary;
  }

  public RunConfiguration getConfiguration() {
    return myConfiguration;
  }

  public Factory<RunnerAndConfigurationSettings> createFactory() {
    return new Factory<RunnerAndConfigurationSettings>() {
      public RunnerAndConfigurationSettings create() {
        RunConfiguration configuration = myConfiguration.getFactory().createConfiguration(ExecutionBundle.message("default.run.configuration.name"), myConfiguration);
        return new RunnerAndConfigurationSettingsImpl(myManager, configuration, false);
      }
    };
  }

  public void setName(String name) {
    myConfiguration.setName(name);
  }

  public String getName() {
    return myConfiguration.getName();
  }

  @Nullable
  private ConfigurationFactory getFactory(final Element element) {
    final String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
    String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
    return myManager.getFactory(typeName, factoryName);
  }

  public void readExternal(Element element) throws InvalidDataException {

    myIsTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE)).booleanValue();
    myTemporary = Boolean.valueOf(element.getAttributeValue(TEMPORARY_ATTRIBUTE)).booleanValue() || TEMP_CONFIGURATION.equals(element.getName());

    final ConfigurationFactory factory = getFactory(element);
    if (factory == null) return;

    if (myIsTemplate) {
      myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
    } else {
      final String name = element.getAttributeValue(NAME_ATTR);
      // souldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue. 
      myConfiguration = myManager.doCreateConfiguration(name, factory);
    }

    myConfiguration.readExternal(element);
    List runners = element.getChildren(RUNNER_ELEMENT);
    myUnloadedRunnerSettings = null;
    for (final Object runner1 : runners) {
      Element runnerElement = (Element) runner1;
      String id = runnerElement.getAttributeValue(RUNNER_ID);
      ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        RunnerSettings settings = createRunnerSettings(runner);
        settings.readExternal(runnerElement);
        myRunnerSettings.put(runner, settings);
      } else {
        if (myUnloadedRunnerSettings == null) myUnloadedRunnerSettings = new ArrayList<Element>(1);
        myUnloadedRunnerSettings.add(runnerElement);
      }
    }

    List configurations = element.getChildren(CONFIGURATION_ELEMENT);
    myUnloadedConfigurationPerRunnerSettings = null;
    for (final Object configuration : configurations) {
      Element configurationElement = (Element) configuration;
      String id = configurationElement.getAttributeValue(RUNNER_ID);
      ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        ConfigurationPerRunnerSettings settings =
            new ConfigurationPerRunnerSettings(id, myConfiguration.createRunnerSettings(new InfoProvider(runner)));
        settings.readExternal(configurationElement);
        myConfigurationPerRunnerSettings.put(runner, settings);
      } else {
        if (myUnloadedConfigurationPerRunnerSettings == null)
          myUnloadedConfigurationPerRunnerSettings = new ArrayList<Element>(1);
        myUnloadedConfigurationPerRunnerSettings.add(configurationElement);
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    final ConfigurationFactory factory = myConfiguration.getFactory();

    if (!(myConfiguration instanceof UnknownRunConfiguration)) {
      element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, String.valueOf(myIsTemplate));
      if (!myIsTemplate) {
        element.setAttribute(NAME_ATTR, myConfiguration.getName());
      }
      element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.getType().getId());
      element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.getName());
      if (myTemporary) {
        element.setAttribute(TEMPORARY_ATTRIBUTE, Boolean.toString(myTemporary));
      }
    }

    myConfiguration.writeExternal(element);

    if (!(myConfiguration instanceof UnknownRunConfiguration)) {
    final Comparator<Element> runnerComparator = createRunnerComparator();
      writeRunnerSettings(runnerComparator, element);
      writeConfigurationPerRunnerSettings(runnerComparator, element);
    }
  }

  private void writeConfigurationPerRunnerSettings(final Comparator<Element> runnerComparator, final Element element)
      throws WriteExternalException {
    final ArrayList<Element> configurationPerRunnerSettings = new ArrayList<Element>();
    for (ProgramRunner runner : myConfigurationPerRunnerSettings.keySet()) {
      ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
      Element runnerElement = new Element(CONFIGURATION_ELEMENT);
      settings.writeExternal(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
      configurationPerRunnerSettings.add(runnerElement);
    }
    if (myUnloadedConfigurationPerRunnerSettings != null) {
      for (Element unloadedCRunnerSetting : myUnloadedConfigurationPerRunnerSettings) {
        configurationPerRunnerSettings.add((Element) unloadedCRunnerSetting.clone());
      }
    }
    Collections.sort(configurationPerRunnerSettings, runnerComparator);
    for (Element runnerConfigurationSetting : configurationPerRunnerSettings) {
      element.addContent(runnerConfigurationSetting);
    }
  }

  private void writeRunnerSettings(final Comparator<Element> runnerComparator, final Element element) throws WriteExternalException {
    final ArrayList<Element> runnerSettings = new ArrayList<Element>();
    for (ProgramRunner runner : myRunnerSettings.keySet()) {
      RunnerSettings settings = myRunnerSettings.get(runner);
      Element runnerElement = new Element(RUNNER_ELEMENT);
      settings.writeExternal(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
      runnerSettings.add(runnerElement);
    }
    if (myUnloadedRunnerSettings != null) {
      for (Element unloadedRunnerSetting : myUnloadedRunnerSettings) {
        runnerSettings.add((Element) unloadedRunnerSetting.clone());
      }
    }
    Collections.sort(runnerSettings, runnerComparator);
    for (Element runnerSetting : runnerSettings) {
      element.addContent(runnerSetting);
    }
  }

  public void checkSettings() throws RuntimeConfigurationException {
    checkSettings(null);
  }

  public void checkSettings(@Nullable Executor executor) throws RuntimeConfigurationException {
    myConfiguration.checkConfiguration();
    if (myConfiguration instanceof RunConfigurationBase) {
      final RunConfigurationBase runConfigurationBase = (RunConfigurationBase) myConfiguration;
      Set<ProgramRunner> runners = new HashSet<ProgramRunner>();
      runners.addAll(myRunnerSettings.keySet());
      runners.addAll(myConfigurationPerRunnerSettings.keySet());
      for (ProgramRunner runner : runners) {
        if (executor == null || runner.canRun(executor.getId(), myConfiguration)) {
          runConfigurationBase.checkRunnerSettings(runner, myRunnerSettings.get(runner), myConfigurationPerRunnerSettings.get(runner));
        }
      }
    }
  }

  private static Comparator<Element> createRunnerComparator() {
    return new Comparator<Element>() {
      public int compare(final Element o1, final Element o2) {
        final String attributeValue1 = o1.getAttributeValue(RUNNER_ID);
        if (attributeValue1 == null) {
          return 1;

        }
        final String attributeValue2 = o2.getAttributeValue(RUNNER_ID);
        if (attributeValue2 == null) {
          return -1;
        }
        return attributeValue1.compareTo(attributeValue2);
      }
    };
  }

  public RunnerSettings getRunnerSettings(ProgramRunner runner) {
    RunnerSettings settings = myRunnerSettings.get(runner);
    if (settings == null) {
      settings = createRunnerSettings(runner);
      myRunnerSettings.put(runner, settings);
    }
    return settings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings(ProgramRunner runner) {
    ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
    if (settings == null) {
      settings = new ConfigurationPerRunnerSettings(runner.getRunnerId(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
      myConfigurationPerRunnerSettings.put(runner, settings);
    }
    return settings;
  }

  @Nullable
  public ConfigurationType getType() {
    return myConfiguration == null ? null : myConfiguration.getType();
  }

  public RunnerAndConfigurationSettings clone() {
    RunnerAndConfigurationSettingsImpl copy = new RunnerAndConfigurationSettingsImpl(myManager, myConfiguration.clone(), false);
    copy.importRunnerAndConfigurationSettings(this);
    return copy;
  }

  public void importRunnerAndConfigurationSettings(RunnerAndConfigurationSettingsImpl template) {
    try {
      for (ProgramRunner runner : template.myRunnerSettings.keySet()) {
        RunnerSettings data = createRunnerSettings(runner);
        myRunnerSettings.put(runner, data);
        Element temp = new Element(DUMMY_ELEMENT_NANE);
        template.myRunnerSettings.get(runner).writeExternal(temp);
        data.readExternal(temp);
      }

      for (ProgramRunner runner : template.myConfigurationPerRunnerSettings.keySet()) {
        ConfigurationPerRunnerSettings data =
            new ConfigurationPerRunnerSettings(runner.getRunnerId(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
        myConfigurationPerRunnerSettings.put(runner, data);
        Element temp = new Element(DUMMY_ELEMENT_NANE);
        template.myConfigurationPerRunnerSettings.get(runner).writeExternal(temp);
        data.readExternal(temp);
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private RunnerSettings createRunnerSettings(final ProgramRunner runner) {
    return new RunnerSettings<JDOMExternalizable>(runner.createConfigurationData(new InfoProvider(runner)), myConfiguration);
  }

  public int compareTo(final Object o) {
    if (o instanceof RunnerAndConfigurationSettings) {
      return getName().compareTo(((RunnerAndConfigurationSettings) o).getName());
    }
    return 0;
  }

  private class InfoProvider implements ConfigurationInfoProvider {
    private final ProgramRunner myRunner;

    public InfoProvider(ProgramRunner runner) {
      myRunner = runner;
    }

    public ProgramRunner getRunner() {
      return myRunner;
    }

    public RunConfiguration getConfiguration() {
      return myConfiguration;
    }

    public RunnerSettings getRunnerSettings() {
      return RunnerAndConfigurationSettingsImpl.this.getRunnerSettings(myRunner);
    }

    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return RunnerAndConfigurationSettingsImpl.this.getConfigurationSettings(myRunner);
    }
  }
}
