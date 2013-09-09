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

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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
  private static final String FOLDER_NAME = "folderName";
  @NonNls
  private static final String TEMPLATE_FLAG_ATTRIBUTE = "default";
  @NonNls
  public static final String NAME_ATTR = "name";
  //@NonNls
  //public static final String UNIQUE_ID = "id";
  @NonNls
  protected static final String DUMMY_ELEMENT_NANE = "dummy";
  @NonNls
  private static final String TEMPORARY_ATTRIBUTE = "temporary";
  @NonNls
  private static final String EDIT_BEFORE_RUN = "editBeforeRun";
  @NonNls
  private static final String SINGLETON = "singleton";


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
  private boolean myEditBeforeRun;
  private boolean mySingleton;
  private String myFolderName;
  //private String myID = null;

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager) {
    myManager = manager;
  }

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager, @NotNull RunConfiguration configuration, boolean isTemplate) {
    myManager = manager;
    myConfiguration = configuration;
    myIsTemplate = isTemplate;
  }

  @Override
  @Nullable
  public ConfigurationFactory getFactory() {
    return myConfiguration == null ? null : myConfiguration.getFactory();
  }

  @Override
  public boolean isTemplate() {
    return myIsTemplate;
  }

  @Override
  public boolean isTemporary() {
    return myTemporary;
  }

  @Override
  public void setTemporary(boolean temporary) {
    myTemporary = temporary;
  }

  @Override
  public RunConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public Factory<RunnerAndConfigurationSettings> createFactory() {
    return new Factory<RunnerAndConfigurationSettings>() {
      @Override
      public RunnerAndConfigurationSettings create() {
        RunConfiguration configuration = myConfiguration.getFactory().createConfiguration(ExecutionBundle.message("default.run.configuration.name"), myConfiguration);
        return new RunnerAndConfigurationSettingsImpl(myManager, configuration, false);
      }
    };
  }

  @Override
  public void setName(String name) {
    myConfiguration.setName(name);
  }

  @Override
  public String getName() {
    return myConfiguration.getName();
  }

  @Override
  public String getUniqueID() {
    return myConfiguration.getType().getDisplayName() + "." + myConfiguration.getName() +
           (myConfiguration instanceof UnknownRunConfiguration ? myConfiguration.getUniqueID() : "");
    //if (myID == null) {
    //  myID = UUID.randomUUID().toString();
    //}
    //return myID;
  }

  @Override
  public void setEditBeforeRun(boolean b) {
    myEditBeforeRun = b;
  }

  @Override
  public boolean isEditBeforeRun() {
    return myEditBeforeRun;
  }

  @Override
  public void setSingleton(boolean singleton) {
    mySingleton = singleton;
  }

  @Override
  public boolean isSingleton() {
    return mySingleton;
  }

  @Override
  public void setFolderName(@Nullable String folderName) {
    myFolderName = folderName;
  }

  @Nullable
  @Override
  public String getFolderName() {
    return myFolderName;
  }

  @Nullable
  private ConfigurationFactory getFactory(final Element element) {
    final String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
    String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
    return myManager.getFactory(typeName, factoryName, !myIsTemplate);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myIsTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE)).booleanValue();
    myTemporary = Boolean.valueOf(element.getAttributeValue(TEMPORARY_ATTRIBUTE)).booleanValue() || TEMP_CONFIGURATION.equals(element.getName());
    myEditBeforeRun = Boolean.valueOf(element.getAttributeValue(EDIT_BEFORE_RUN)).booleanValue();
    myFolderName = element.getAttributeValue(FOLDER_NAME);
    //assert myID == null: "myId must be null at readExternal() stage";
    //myID = element.getAttributeValue(UNIQUE_ID, UUID.randomUUID().toString());
    // singleton is not configurable by user for template
    if (!myIsTemplate) {
      mySingleton = Boolean.valueOf(element.getAttributeValue(SINGLETON)).booleanValue();
    }

    final ConfigurationFactory factory = getFactory(element);
    if (factory == null) return;

    if (myIsTemplate) {
      mySingleton = factory.isConfigurationSingletonByDefault();
      myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
    } else {
      final String name = element.getAttributeValue(NAME_ATTR);
      // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue.
      myConfiguration = myManager.doCreateConfiguration(name, factory, false);
    }

    myConfiguration.readExternal(element);
    List<Element> runners = element.getChildren(RUNNER_ELEMENT);
    myUnloadedRunnerSettings = null;
    for (final Element runnerElement : runners) {
      String id = runnerElement.getAttributeValue(RUNNER_ID);
      ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        RunnerSettings settings = createRunnerSettings(runner);
        if (settings != null) {
          settings.readExternal(runnerElement);
        }
        myRunnerSettings.put(runner, settings);
      }
      else {
        if (myUnloadedRunnerSettings == null) myUnloadedRunnerSettings = new ArrayList<Element>(1);
        IdeaPluginDescriptorImpl.internJDOMElement(runnerElement);
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
        ConfigurationPerRunnerSettings settings = myConfiguration.createRunnerSettings(new InfoProvider(runner));
        if (settings != null) {
          settings.readExternal(configurationElement);
        }
        myConfigurationPerRunnerSettings.put(runner, settings);
      } else {
        if (myUnloadedConfigurationPerRunnerSettings == null)
          myUnloadedConfigurationPerRunnerSettings = new ArrayList<Element>(1);
        myUnloadedConfigurationPerRunnerSettings.add(configurationElement);
      }
    }
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    final ConfigurationFactory factory = myConfiguration.getFactory();

    if (!(myConfiguration instanceof UnknownRunConfiguration)) {
      element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, String.valueOf(myIsTemplate));
      if (!myIsTemplate) {
        element.setAttribute(NAME_ATTR, myConfiguration.getName());
      }
      element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.getType().getId());
      element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.getName());
      if (myFolderName != null) {
        element.setAttribute(FOLDER_NAME, myFolderName);
      }
      //element.setAttribute(UNIQUE_ID, getUniqueID());

      if (isEditBeforeRun()) element.setAttribute(EDIT_BEFORE_RUN, String.valueOf(true));
      if (isSingleton()) element.setAttribute(SINGLETON, String.valueOf(true));
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
      if (settings != null) {
        settings.writeExternal(runnerElement);
      }
      runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
      configurationPerRunnerSettings.add(runnerElement);
    }
    if (myUnloadedConfigurationPerRunnerSettings != null) {
      for (Element unloadedCRunnerSetting : myUnloadedConfigurationPerRunnerSettings) {
        configurationPerRunnerSettings.add(unloadedCRunnerSetting.clone());
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
      if (settings != null) {
        settings.writeExternal(runnerElement);
      }
      runnerElement.setAttribute(RUNNER_ID, runner.getRunnerId());
      runnerSettings.add(runnerElement);
    }
    if (myUnloadedRunnerSettings != null) {
      for (Element unloadedRunnerSetting : myUnloadedRunnerSettings) {
        runnerSettings.add(unloadedRunnerSetting.clone());
      }
    }
    Collections.sort(runnerSettings, runnerComparator);
    for (Element runnerSetting : runnerSettings) {
      element.addContent(runnerSetting);
    }
  }

  @Override
  public void checkSettings() throws RuntimeConfigurationException {
    checkSettings(null);
  }

  @Override
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
      if (executor != null) {
        runConfigurationBase.checkSettingsBeforeRun();
      }
    }
  }

  @Override
  public boolean canRunOn(@NotNull ExecutionTarget target) {
    if (myConfiguration instanceof TargetAwareRunProfile) {
      return ((TargetAwareRunProfile)myConfiguration).canRunOn(target);
    }
    return true;
  }

  private static Comparator<Element> createRunnerComparator() {
    return new Comparator<Element>() {
      @Override
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

  @Override
  public RunnerSettings getRunnerSettings(@NotNull ProgramRunner runner) {
    if (!myRunnerSettings.containsKey(runner)) {
      try {
        RunnerSettings runnerSettings = createRunnerSettings(runner);
        myRunnerSettings.put(runner, runnerSettings);
        return runnerSettings;
      }
      catch (AbstractMethodError e) {
        LOG.error("Update failed for: " + myConfiguration.getType().getDisplayName() + ", runner: " + runner.getRunnerId(), e);
      }
    }
    return myRunnerSettings.get(runner);
  }

  @Override
  @Nullable
  public ConfigurationPerRunnerSettings getConfigurationSettings(@NotNull ProgramRunner runner) {
    if (!myConfigurationPerRunnerSettings.containsKey(runner)) {
      ConfigurationPerRunnerSettings settings = myConfiguration.createRunnerSettings(new InfoProvider(runner));
      myConfigurationPerRunnerSettings.put(runner, settings);
      return settings;
    }
    return myConfigurationPerRunnerSettings.get(runner);
  }

  @Override
  @Nullable
  public ConfigurationType getType() {
    return myConfiguration == null ? null : myConfiguration.getType();
  }

  @Override
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
        if (data != null) {
          Element temp = new Element(DUMMY_ELEMENT_NANE);
          RunnerSettings templateSettings = template.myRunnerSettings.get(runner);
          if (templateSettings != null) {
            templateSettings.writeExternal(temp);
            data.readExternal(temp);
          }
        }
      }

      for (ProgramRunner runner : template.myConfigurationPerRunnerSettings.keySet()) {
        ConfigurationPerRunnerSettings data = myConfiguration.createRunnerSettings(new InfoProvider(runner));
        myConfigurationPerRunnerSettings.put(runner, data);
        if (data != null) {
          Element temp = new Element(DUMMY_ELEMENT_NANE);
          ConfigurationPerRunnerSettings templateSettings = template.myConfigurationPerRunnerSettings.get(runner);
          if (templateSettings != null) {
            templateSettings.writeExternal(temp);
            data.readExternal(temp);
          }
        }
      }
      setSingleton(template.isSingleton());
      setEditBeforeRun(template.isEditBeforeRun());
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private RunnerSettings createRunnerSettings(final ProgramRunner runner) {
    return runner.createConfigurationData(new InfoProvider(runner));
  }

  @Override
  public int compareTo(final Object o) {
    if (o instanceof RunnerAndConfigurationSettings) {
      return getName().compareTo(((RunnerAndConfigurationSettings) o).getName());
    }
    return 0;
  }

  @Override
  public String toString() {
    ConfigurationType type = getType();
    return (type != null ? type.getDisplayName() + ": " : "") + (isTemplate() ? "<template>" : getName());
  }

  private class InfoProvider implements ConfigurationInfoProvider {
    private final ProgramRunner myRunner;

    public InfoProvider(ProgramRunner runner) {
      myRunner = runner;
    }

    @Override
    public ProgramRunner getRunner() {
      return myRunner;
    }

    @Override
    public RunConfiguration getConfiguration() {
      return myConfiguration;
    }

    @Override
    public RunnerSettings getRunnerSettings() {
      return RunnerAndConfigurationSettingsImpl.this.getRunnerSettings(myRunner);
    }

    @Override
    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return RunnerAndConfigurationSettingsImpl.this.getConfigurationSettings(myRunner);
    }
  }
}
