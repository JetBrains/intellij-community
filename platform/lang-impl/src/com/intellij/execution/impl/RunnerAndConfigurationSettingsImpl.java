/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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
  private static final String RUNNER_ID = "RunnerId";

  private static final Comparator<Element> RUNNER_COMPARATOR = (o1, o2) -> {
    String attributeValue1 = o1.getAttributeValue(RUNNER_ID);
    if (attributeValue1 == null) {
      return 1;
    }
    return StringUtil.compare(attributeValue1, o2.getAttributeValue(RUNNER_ID), false);
  };

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
  protected static final String DUMMY_ELEMENT_NAME = "dummy";
  @NonNls
  private static final String TEMPORARY_ATTRIBUTE = "temporary";
  @NonNls
  private static final String EDIT_BEFORE_RUN = "editBeforeRun";
  @NonNls
  private static final String ACTIVATE_TOOLWINDOW_BEFORE_RUN = "activateToolWindowBeforeRun";
  @NonNls
  public static final String SINGLETON = "singleton";

  /** for compatibility */
  @NonNls
  private static final String TEMP_CONFIGURATION = "tempConfiguration";

  private final RunManagerImpl myManager;
  private RunConfiguration myConfiguration;
  private boolean myIsTemplate;

  private final RunnerItem<RunnerSettings> myRunnerSettings = new RunnerItem<RunnerSettings>("RunnerSettings") {
    @Override
    protected RunnerSettings createSettings(@NotNull ProgramRunner runner) {
      return runner.createConfigurationData(new InfoProvider(runner));
    }
  };

  private final RunnerItem<ConfigurationPerRunnerSettings> myConfigurationPerRunnerSettings = new RunnerItem<ConfigurationPerRunnerSettings>("ConfigurationWrapper") {
    @Override
    protected ConfigurationPerRunnerSettings createSettings(@NotNull ProgramRunner runner) {
      return myConfiguration.createRunnerSettings(new InfoProvider(runner));
    }
  };

  private boolean myTemporary;
  private boolean myEditBeforeRun;
  private boolean myActivateToolWindowBeforeRun = true;
  private boolean mySingleton;
  private boolean myWasSingletonSpecifiedExplicitly;
  private String myFolderName;
  //private String myID = null;

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager) {
    myManager = manager;
  }

  @SuppressWarnings("deprecation")
  private abstract class RunnerItem<T> {
    private final Map<ProgramRunner, T> settings = new THashMap<>();

    private List<Element> unloadedSettings;
    // to avoid changed files
    private final Set<String> loadedIds = new THashSet<>();

    private final String childTagName;

    RunnerItem(@NotNull String childTagName) {
      this.childTagName = childTagName;
    }

    public void loadState(@NotNull Element element) throws InvalidDataException {
      settings.clear();
      if (unloadedSettings != null) {
        unloadedSettings.clear();
      }
      loadedIds.clear();

      for (Iterator<Element> iterator = element.getChildren(childTagName).iterator(); iterator.hasNext(); ) {
        Element state = iterator.next();
        ProgramRunner runner = findRunner(state.getAttributeValue(RUNNER_ID));
        if (runner == null) {
          iterator.remove();
        }
        add(state, runner, runner == null ? null : createSettings(runner));
      }
    }

    private ProgramRunner findRunner(final String runnerId) {
      List<ProgramRunner> runnersById
        = ContainerUtil.filter(ProgramRunner.PROGRAM_RUNNER_EP.getExtensions(), runner -> Comparing.equal(runnerId, runner.getRunnerId()));

      int runnersByIdCount = runnersById.size();
      if (runnersByIdCount == 0) {
        return null;
      }
      else if (runnersByIdCount == 1) {
        return ContainerUtil.getFirstItem(runnersById);
      }
      else {
        LOG.error("More than one runner found for ID: " + runnerId);
        for (final Executor executor : ExecutorRegistry.getInstance().getRegisteredExecutors()) {
          for (ProgramRunner runner : runnersById) {
            if (runner.canRun(executor.getId(), myConfiguration)) {
              return runner;
            }
          }
        }
        return null;
      }
    }

    public void getState(@NotNull Element element) throws WriteExternalException {
      List<Element> runnerSettings = new SmartList<>();
      for (ProgramRunner runner : settings.keySet()) {
        T settings = this.settings.get(runner);
        boolean wasLoaded = loadedIds.contains(runner.getRunnerId());
        if (settings == null && !wasLoaded) {
          continue;
        }

        Element state = new Element(childTagName);
        if (settings != null) {
          ((JDOMExternalizable)settings).writeExternal(state);
        }
        if (wasLoaded || !JDOMUtil.isEmpty(state)) {
          state.setAttribute(RUNNER_ID, runner.getRunnerId());
          runnerSettings.add(state);
        }
      }
      if (unloadedSettings != null) {
        for (Element unloadedSetting : unloadedSettings) {
          runnerSettings.add(unloadedSetting.clone());
        }
      }
      Collections.sort(runnerSettings, RUNNER_COMPARATOR);
      for (Element runnerSetting : runnerSettings) {
        element.addContent(runnerSetting);
      }
    }

    protected abstract T createSettings(@NotNull ProgramRunner runner);

    private void add(@NotNull Element state, @Nullable ProgramRunner runner, @Nullable T data) throws InvalidDataException {
      if (runner == null) {
        if (unloadedSettings == null) {
          unloadedSettings = new SmartList<>();
        }
        unloadedSettings.add(state);
        return;
      }

      if (data != null) {
        ((JDOMExternalizable)data).readExternal(state);
      }

      settings.put(runner, data);
      loadedIds.add(runner.getRunnerId());
    }

    public T getOrCreateSettings(@NotNull ProgramRunner runner) {
      T result = settings.get(runner);
      if (result == null) {
        try {
          result = createSettings(runner);
          settings.put(runner, result);
        }
        catch (AbstractMethodError ignored) {
          LOG.error("Update failed for: " + myConfiguration.getType().getDisplayName() + ", runner: " + runner.getRunnerId(), new ExtensionException(runner.getClass()));
        }
      }
      return result;
    }
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
    return () -> {
      RunConfiguration configuration = myConfiguration.getFactory().createConfiguration(ExecutionBundle.message("default.run.configuration.name"), myConfiguration);
      return new RunnerAndConfigurationSettingsImpl(myManager, configuration, false);
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
    //noinspection deprecation
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
  public void setActivateToolWindowBeforeRun(boolean activate) {
    myActivateToolWindowBeforeRun = activate;
  }

  @Override
  public boolean isActivateToolWindowBeforeRun() {
    return myActivateToolWindowBeforeRun;
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
    String value = element.getAttributeValue(ACTIVATE_TOOLWINDOW_BEFORE_RUN);
    myActivateToolWindowBeforeRun = value == null || Boolean.valueOf(value).booleanValue();
    myFolderName = element.getAttributeValue(FOLDER_NAME);
    //assert myID == null: "myId must be null at readExternal() stage";
    //myID = element.getAttributeValue(UNIQUE_ID, UUID.randomUUID().toString());
    final ConfigurationFactory factory = getFactory(element);
    if (factory == null) return;

    myWasSingletonSpecifiedExplicitly = false;
    if (myIsTemplate) {
      mySingleton = factory.isConfigurationSingletonByDefault();
    }
    else {
      String singletonStr = element.getAttributeValue(SINGLETON);
      if (StringUtil.isEmpty(singletonStr)) {
        mySingleton = factory.isConfigurationSingletonByDefault();
      }
      else {
        myWasSingletonSpecifiedExplicitly = true;
        mySingleton = Boolean.parseBoolean(singletonStr);
      }
    }

    if (myIsTemplate) {
      myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
    }
    else {
      // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue.
      myConfiguration = myManager.doCreateConfiguration(element.getAttributeValue(NAME_ATTR), factory, false);
    }

    myConfiguration.readExternal(element);
    myRunnerSettings.loadState(element);
    myConfigurationPerRunnerSettings.loadState(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
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

      if (isEditBeforeRun()) {
        element.setAttribute(EDIT_BEFORE_RUN, String.valueOf(true));
      }
      if (!isActivateToolWindowBeforeRun()) {
        element.setAttribute(ACTIVATE_TOOLWINDOW_BEFORE_RUN, String.valueOf(false));
      }
      if (myWasSingletonSpecifiedExplicitly || mySingleton != factory.isConfigurationSingletonByDefault()) {
        element.setAttribute(SINGLETON, String.valueOf(mySingleton));
      }
      if (myTemporary) {
        element.setAttribute(TEMPORARY_ATTRIBUTE, Boolean.toString(true));
      }
    }

    myConfiguration.writeExternal(element);

    if (!(myConfiguration instanceof UnknownRunConfiguration)) {
      myRunnerSettings.getState(element);
      myConfigurationPerRunnerSettings.getState(element);
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
      Set<ProgramRunner> runners = new THashSet<>();
      runners.addAll(myRunnerSettings.settings.keySet());
      runners.addAll(myConfigurationPerRunnerSettings.settings.keySet());
      for (ProgramRunner runner : runners) {
        if (executor == null || runner.canRun(executor.getId(), myConfiguration)) {
          runConfigurationBase.checkRunnerSettings(runner, myRunnerSettings.settings.get(runner), myConfigurationPerRunnerSettings.settings.get(runner));
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

  @Override
  public RunnerSettings getRunnerSettings(@NotNull ProgramRunner runner) {
    return myRunnerSettings.getOrCreateSettings(runner);
  }

  @Override
  @Nullable
  public ConfigurationPerRunnerSettings getConfigurationSettings(@NotNull ProgramRunner runner) {
    return myConfigurationPerRunnerSettings.getOrCreateSettings(runner);
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
    importFromTemplate(template.myRunnerSettings, myRunnerSettings);
    importFromTemplate(template.myConfigurationPerRunnerSettings, myConfigurationPerRunnerSettings);

    setSingleton(template.isSingleton());
    setEditBeforeRun(template.isEditBeforeRun());
    setActivateToolWindowBeforeRun(template.isActivateToolWindowBeforeRun());
  }

  @SuppressWarnings("deprecation")
  private <T> void importFromTemplate(@NotNull RunnerItem<T> templateItem, @NotNull RunnerItem<T> item) {
    for (ProgramRunner runner : templateItem.settings.keySet()) {
      T data = item.createSettings(runner);
      item.settings.put(runner, data);
      if (data != null) {
        Element temp = new Element(DUMMY_ELEMENT_NAME);
        T templateSettings = templateItem.settings.get(runner);
        if (templateSettings != null) {
          try {
            ((JDOMExternalizable)templateSettings).writeExternal(temp);
            ((JDOMExternalizable)data).readExternal(temp);
          }
          catch (WriteExternalException e) {
            LOG.error(e);
          }
          catch (InvalidDataException e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  @Override
  public int compareTo(@NotNull final Object o) {
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
