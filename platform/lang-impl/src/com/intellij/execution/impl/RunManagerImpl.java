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

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.ui.IconDeferrer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;


public class RunManagerImpl extends RunManagerEx implements JDOMExternalizable, ProjectComponent {
  private final Project myProject;

  private final Map<String, ConfigurationType> myTypesByName = new LinkedHashMap<String, ConfigurationType>();

  private final Map<String, RunnerAndConfigurationSettings> myTemplateConfigurationsMap =
    new HashMap<String, RunnerAndConfigurationSettings>();
  private final Map<Integer, RunnerAndConfigurationSettings> myConfigurations =
    new LinkedHashMap<Integer, RunnerAndConfigurationSettings>(); // template configurations are not included here
  private final Map<Integer, Boolean> mySharedConfigurations = new TreeMap<Integer, Boolean>();
  /**
   * configurationID -> [BeforeTaskProviderName->BeforeRunTask]
   */
  private final Map<RunConfiguration, Map<Key<? extends BeforeRunTask>, BeforeRunTask>> myConfigurationToBeforeTasksMap =
    new WeakHashMap<RunConfiguration, Map<Key<? extends BeforeRunTask>, BeforeRunTask>>();

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  @Nullable private String myLoadedSelectedConfigurationUniqueName = null;
  @Nullable private Integer mySelectedConfigurationId = null;

  private Map<Integer, Icon> myIdToIcon = new com.intellij.util.containers.HashMap<Integer, Icon>();

  @NonNls
  protected static final String CONFIGURATION = "configuration";
  private ConfigurationType[] myTypes;
  private final RunManagerConfig myConfig;
  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String SELECTED_ATTR = "selected";
  @NonNls private static final String METHOD = "method";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String VALUE = "value";

  private List<Element> myUnknownElements = null;
  private JDOMExternalizableStringList myOrder = new JDOMExternalizableStringList();
  private boolean myOrdered = true;

  private final EventDispatcher<RunManagerListener> myDispatcher = EventDispatcher.create(RunManagerListener.class);

  public RunManagerImpl(final Project project,
                        PropertiesComponent propertiesComponent) {
    myConfig = new RunManagerConfig(propertiesComponent, this);
    myProject = project;

    initConfigurationTypes();
  }

  // separate method needed for tests
  public final void initializeConfigurationTypes(@NotNull final ConfigurationType[] factories) {
    Arrays.sort(factories, new Comparator<ConfigurationType>() {
      public int compare(final ConfigurationType o1, final ConfigurationType o2) {
        return o1.getDisplayName().compareTo(o2.getDisplayName());
      }
    });

    final ArrayList<ConfigurationType> types = new ArrayList<ConfigurationType>(Arrays.asList(factories));
    types.add(UnknownConfigurationType.INSTANCE);
    myTypes = types.toArray(new ConfigurationType[types.size()]);

    for (final ConfigurationType type : factories) {
      myTypesByName.put(type.getId(), type);
    }

    final UnknownConfigurationType broken = UnknownConfigurationType.INSTANCE;
    myTypesByName.put(broken.getId(), broken);
  }

  private void initConfigurationTypes() {
    final ConfigurationType[] configurationTypes = Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP);
    initializeConfigurationTypes(configurationTypes);
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void projectOpened() {
  }

  @NotNull
  public RunnerAndConfigurationSettings createConfiguration(final String name, final ConfigurationFactory factory) {
    return createConfiguration(doCreateConfiguration(name, factory, true), factory);
  }

  protected RunConfiguration doCreateConfiguration(String name, ConfigurationFactory factory, final boolean fromTemplate) {
    if (fromTemplate) {
      return factory.createConfiguration(name, getConfigurationTemplate(factory).getConfiguration());
    }
    else {
      final RunConfiguration configuration = factory.createTemplateConfiguration(myProject, this);
      configuration.setName(name);
      return configuration;
    }
  }

  @NotNull
  public RunnerAndConfigurationSettings createConfiguration(final RunConfiguration runConfiguration,
                                                            final ConfigurationFactory factory) {
    RunnerAndConfigurationSettings template = getConfigurationTemplate(factory);
    myConfigurationToBeforeTasksMap.put(runConfiguration, getBeforeRunTasks(template.getConfiguration()));
    shareConfiguration(runConfiguration, isConfigurationShared(template));
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this, runConfiguration, false);
    settings.importRunnerAndConfigurationSettings((RunnerAndConfigurationSettingsImpl)template);
    return settings;
  }

  public void projectClosed() {
    myTemplateConfigurationsMap.clear();
  }

  public RunManagerConfig getConfig() {
    return myConfig;
  }

  @NotNull
  public ConfigurationType[] getConfigurationFactories() {
    return myTypes.clone();
  }

  public ConfigurationType[] getConfigurationFactories(final boolean includeUnknown) {
    final ConfigurationType[] configurationTypes = myTypes.clone();
    if (!includeUnknown) {
      final List<ConfigurationType> types = new ArrayList<ConfigurationType>();
      for (ConfigurationType configurationType : configurationTypes) {
        if (!(configurationType instanceof UnknownConfigurationType)) {
          types.add(configurationType);
        }
      }

      return types.toArray(new ConfigurationType[types.size()]);
    }

    return configurationTypes;
  }

  /**
   * Template configuration is not included
   */
  @NotNull
  public RunConfiguration[] getConfigurations(@NotNull final ConfigurationType type) {

    final List<RunConfiguration> array = new ArrayList<RunConfiguration>();
    for (RunnerAndConfigurationSettings myConfiguration : getSortedConfigurations()) {
      final RunConfiguration configuration = myConfiguration.getConfiguration();
      final ConfigurationType configurationType = configuration.getType();
      if (type.getId().equals(configurationType.getId())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunConfiguration[array.size()]);
  }

  @NotNull
  public RunConfiguration[] getAllConfigurations() {
    RunConfiguration[] result = new RunConfiguration[myConfigurations.size()];
    int i = 0;
    for (Iterator<RunnerAndConfigurationSettings> iterator = getSortedConfigurations().iterator(); iterator.hasNext(); i++) {
      RunnerAndConfigurationSettings settings = iterator.next();
      result[i] = settings.getConfiguration();
    }

    return result;
  }

  @Nullable
  public RunnerAndConfigurationSettings getSettings(@Nullable RunConfiguration configuration) {
    if (configuration == null) return null;
    for (RunnerAndConfigurationSettings settings : getSortedConfigurations()) {
      if (settings.getConfiguration() == configuration) return settings;
    }
    return null;
  }

  /**
   * Template configuration is not included
   */
  public RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull final ConfigurationType type) {

    final LinkedHashSet<RunnerAndConfigurationSettings> array = new LinkedHashSet<RunnerAndConfigurationSettings>();
    for (RunnerAndConfigurationSettings configuration : getSortedConfigurations()) {
      final ConfigurationType configurationType = configuration.getType();
      if (configurationType != null && type.getId().equals(configurationType.getId())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunnerAndConfigurationSettings[array.size()]);
  }

  public RunnerAndConfigurationSettings getConfigurationTemplate(final ConfigurationFactory factory) {
    RunnerAndConfigurationSettings template = myTemplateConfigurationsMap.get(factory.getType().getId() + "." + factory.getName());
    if (template == null) {
      template = new RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(myProject, this), true);
      if (template.getConfiguration() instanceof UnknownRunConfiguration) {
        ((UnknownRunConfiguration)template.getConfiguration()).setDoNotStore(true);
      }
      myTemplateConfigurationsMap.put(factory.getType().getId() + "." + factory.getName(), template);
    }
    return template;
  }

  public void addConfiguration(RunnerAndConfigurationSettings settings,
                               boolean shared,
                               Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks) {
    final RunConfiguration configuration = settings.getConfiguration();

    Integer existingId = findConfigurationIdByUniqueName(getUniqueName(settings));
    Integer newId = configuration.getUniqueID();

    if (existingId != null) {
      myConfigurations.remove(existingId);
      mySharedConfigurations.remove(existingId);
    }

    if (mySelectedConfigurationId != null && mySelectedConfigurationId.equals(existingId)) setSelectedConfigurationId(newId);
    myConfigurations.put(newId, settings);
    checkRecentsLimit();

    mySharedConfigurations.put(newId, shared);
    setBeforeRunTasks(configuration, tasks);
    myDispatcher.getMulticaster().runConfigurationAdded(settings);
  }

  void checkRecentsLimit() {
    List<RunnerAndConfigurationSettings> removed = new ArrayList<RunnerAndConfigurationSettings>();
    while (getTempConfigurations().length > getConfig().getRecentsLimit()) {
      for (Iterator<Map.Entry<Integer, RunnerAndConfigurationSettings>> it = myConfigurations.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<Integer, RunnerAndConfigurationSettings> entry = it.next();
        if (entry.getValue().isTemporary()) {
          removed.add(entry.getValue());
          it.remove();
          break;
        }
      }
    }
    fireRunConfigurationsRemoved(removed);
  }

  static String getUniqueName(@NotNull RunnerAndConfigurationSettings settings) {
    RunConfiguration config = settings.getConfiguration();
    return config.getType().getDisplayName() + "." + settings.getName() +
           (config instanceof UnknownRunConfiguration ? config.getUniqueID() : "");
  }

  public void removeConfigurations(@NotNull final ConfigurationType type) {
    List<RunnerAndConfigurationSettings> removed = new ArrayList<RunnerAndConfigurationSettings>();
    for (Iterator<RunnerAndConfigurationSettings> it = getSortedConfigurations().iterator(); it.hasNext(); ) {
      final RunnerAndConfigurationSettings configuration = it.next();
      final ConfigurationType configurationType = configuration.getType();
      if (configurationType != null && type.getId().equals(configurationType.getId())) {
        removed.add(configuration);
        invalidateConfigurationIcon(configuration);
        it.remove();
      }
    }
    fireRunConfigurationsRemoved(removed);

    final RunnerAndConfigurationSettings selectedConfiguration = getSelectedConfiguration();
    if (selectedConfiguration != null && removed.contains(selectedConfiguration)) {
      final Collection<RunnerAndConfigurationSettings> sortedConfigurations = getSortedConfigurations();
      RunnerAndConfigurationSettings toSelect = null;
      if (sortedConfigurations.size() > 0) {
        toSelect = sortedConfigurations.iterator().next();
      }

      setSelectedConfiguration(toSelect);
    }
  }

  @Override
  public void removeConfiguration(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) return;

    for (Iterator<RunnerAndConfigurationSettings> it = getSortedConfigurations().iterator(); it.hasNext(); ) {
      final RunnerAndConfigurationSettings configuration = it.next();
      if (configuration.equals(settings)) {
        if (mySelectedConfigurationId != null && mySelectedConfigurationId == settings.getConfiguration().getUniqueID()) {
          setSelectedConfiguration(null);
        }

        it.remove();
        invalidateConfigurationIcon(configuration);
        myDispatcher.getMulticaster().runConfigurationRemoved(configuration);
        break;
      }
    }
  }

  @Nullable
  public RunnerAndConfigurationSettings getSelectedConfiguration() {
    if (mySelectedConfigurationId == null && myLoadedSelectedConfigurationUniqueName != null) {
      setSelectedConfigurationId(findConfigurationIdByUniqueName(myLoadedSelectedConfigurationUniqueName));
    }
    return mySelectedConfigurationId == null ? null : myConfigurations.get(mySelectedConfigurationId);
  }

  public void setSelectedConfiguration(@Nullable RunnerAndConfigurationSettings settings) {
    setSelectedConfigurationId(settings == null ? null : settings.getConfiguration().getUniqueID());
    if (settings != null) {
      invalidateConfigurationIcon(settings);
    }
    fireRunConfigurationSelected();
  }

  private void setSelectedConfigurationId(@Nullable Integer id) {
    mySelectedConfigurationId = id;
    if (mySelectedConfigurationId != null) myLoadedSelectedConfigurationUniqueName = null;
  }

  @Override
  public Collection<RunnerAndConfigurationSettings> getSortedConfigurations() {
    if (!myOrdered) { //compatibility
      List<Pair<String, RunnerAndConfigurationSettings>> order
        = new ArrayList<Pair<String, RunnerAndConfigurationSettings>>(myConfigurations.size());
      for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
        order.add(Pair.create(getUniqueName(each), each));
      }
      myConfigurations.clear();

      if (myOrder.isEmpty()) {
        // IDEA-63663 Sort run configurations alphabetically if clean checkout 
        Collections.sort(order, new Comparator<Pair<String, RunnerAndConfigurationSettings>>() {
          @Override
          public int compare(Pair<String, RunnerAndConfigurationSettings> o1, Pair<String, RunnerAndConfigurationSettings> o2) {
            return o1.first.compareTo(o2.first);
          }
        });
      }
      else {
        Collections.sort(order, new Comparator<Pair<String, RunnerAndConfigurationSettings>>() {
          @Override
          public int compare(Pair<String, RunnerAndConfigurationSettings> o1, Pair<String, RunnerAndConfigurationSettings> o2) {
            return myOrder.indexOf(o1.first) - myOrder.indexOf(o2.first);
          }
        });
      }

      for (Pair<String, RunnerAndConfigurationSettings> each : order) {
        RunnerAndConfigurationSettings setting = each.second;
        myConfigurations.put(setting.getConfiguration().getUniqueID(), setting);
      }

      myOrdered = true;
    }
    return myConfigurations.values();
  }

  public static boolean canRunConfiguration(@NotNull final RunnerAndConfigurationSettings configuration, @NotNull final Executor executor) {
    try {
      configuration.checkSettings(executor);
    }
    catch (RuntimeConfigurationError er) {
      return false;
    }
    catch (RuntimeConfigurationException e) {
      return true;
    }
    return true;
  }

  public void writeExternal(@NotNull final Element parentNode) throws WriteExternalException {
    writeContext(parentNode);
    for (final RunnerAndConfigurationSettings runnerAndConfigurationSettings : myTemplateConfigurationsMap.values()) {
      if (runnerAndConfigurationSettings.getConfiguration() instanceof UnknownRunConfiguration) {
        if (((UnknownRunConfiguration)runnerAndConfigurationSettings.getConfiguration()).isDoNotStore()) {
          continue;
        }
      }

      addConfigurationElement(parentNode, runnerAndConfigurationSettings);
    }

    final Collection<RunnerAndConfigurationSettings> configurations = getStableConfigurations();
    for (RunnerAndConfigurationSettings configuration : configurations) {
      if (!isConfigurationShared(configuration)) {
        addConfigurationElement(parentNode, configuration);
      }
    }

    final JDOMExternalizableStringList order = new JDOMExternalizableStringList();
    //temp && stable configurations, !unknown
    for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
      if (each.getType() instanceof UnknownConfigurationType) continue;
      order.add(getUniqueName(each));
    }

    order.writeExternal(parentNode);

    if (myUnknownElements != null) {
      for (Element unloadedElement : myUnknownElements) {
        parentNode.addContent((Element)unloadedElement.clone());
      }
    }
  }

  public void writeContext(Element parentNode) throws WriteExternalException {
    for (RunnerAndConfigurationSettings configurationSettings : myConfigurations.values()) {
      if (configurationSettings.isTemporary()) {
        addConfigurationElement(parentNode, configurationSettings, CONFIGURATION);
      }
    }
    RunnerAndConfigurationSettings selected = getSelectedConfiguration();
    if (selected != null) {
      parentNode.setAttribute(SELECTED_ATTR, getUniqueName(selected));
    }
  }

  void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettings template) throws WriteExternalException {
    addConfigurationElement(parentNode, template, CONFIGURATION);
  }

  private void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettings settings, String elementType)
    throws WriteExternalException {
    final Element configurationElement = new Element(elementType);
    parentNode.addContent(configurationElement);
    ((RunnerAndConfigurationSettingsImpl)settings).writeExternal(configurationElement);

    if (!(settings.getConfiguration() instanceof UnknownRunConfiguration)) {
      final Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks = getBeforeRunTasks(settings.getConfiguration());
      Map<Key<? extends BeforeRunTask>, BeforeRunTask> templateTasks;
      if (!settings.isTemplate()) {
        final RunnerAndConfigurationSettings templateSettings = getConfigurationTemplate(settings.getFactory());
        templateTasks = getBeforeRunTasks(templateSettings.getConfiguration());
      }
      else {
        templateTasks = null;
      }
      final List<Key<? extends BeforeRunTask>> order = new ArrayList<Key<? extends BeforeRunTask>>(tasks.keySet());
      Collections.sort(order, new Comparator<Key<? extends BeforeRunTask>>() {
        public int compare(Key<? extends BeforeRunTask> o1, Key<? extends BeforeRunTask> o2) {
          return o1.toString().compareToIgnoreCase(o2.toString());
        }
      });
      final Element methodsElement = new Element(METHOD);
      for (Key<? extends BeforeRunTask> providerID : order) {
        final BeforeRunTask beforeRunTask = tasks.get(providerID);

        if (templateTasks != null) {
          final BeforeRunTask templateTask = templateTasks.get(providerID);
          if (beforeRunTask.equals(templateTask)) {
            continue; // not neccesary saving if the task is the same as template
          }
        }

        final Element child = new Element(OPTION);
        child.setAttribute(NAME_ATTR, providerID.toString());
        beforeRunTask.writeExternal(child);
        methodsElement.addContent(child);
      }
      configurationElement.addContent(methodsElement);
    }
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    clear();

    final Comparator<Element> comparator = new Comparator<Element>() {
      public int compare(Element a, Element b) {
        final boolean aDefault = Boolean.valueOf(a.getAttributeValue("default", "false"));
        final boolean bDefault = Boolean.valueOf(b.getAttributeValue("default", "false"));
        return aDefault == bDefault ? 0 : aDefault ? -1 : 1;
      }
    };

    final List children = parentNode.getChildren();
    final List<Element> sortedElements = new ArrayList<Element>();
    for (final Object aChildren : children) {
      final Element element = (Element)aChildren;
      if (Comparing.strEqual(element.getName(), CONFIGURATION)) {
        sortedElements.add(element);
      }
    }

    Collections.sort(sortedElements, comparator); // ensure templates are loaded first!

    for (final Element element : sortedElements) {
      if (loadConfiguration(element, false) == null) {
        if (myUnknownElements == null) myUnknownElements = new ArrayList<Element>(2);
        myUnknownElements.add(element);
      }
    }

    myOrder.readExternal(parentNode);
    myOrdered = false;

    myLoadedSelectedConfigurationUniqueName = parentNode.getAttributeValue(SELECTED_ATTR);
    setSelectedConfigurationId(findConfigurationIdByUniqueName(myLoadedSelectedConfigurationUniqueName));

    fireBeforeRunTasksUpdated();
    fireRunConfigurationSelected();
  }

  public void readContext(Element parentNode) throws InvalidDataException {
    myLoadedSelectedConfigurationUniqueName = parentNode.getAttributeValue(SELECTED_ATTR);

    for (Object aChildren : parentNode.getChildren()) {
      Element element = (Element)aChildren;
      RunnerAndConfigurationSettings config = loadConfiguration(element, false);
      if (myLoadedSelectedConfigurationUniqueName == null
          && config != null
          && Boolean.valueOf(element.getAttributeValue(SELECTED_ATTR)).booleanValue()) {
        myLoadedSelectedConfigurationUniqueName = getUniqueName(config);
      }
    }

    setSelectedConfigurationId(findConfigurationIdByUniqueName(myLoadedSelectedConfigurationUniqueName));

    fireRunConfigurationSelected();
  }

  @Nullable
  private Integer findConfigurationIdByUniqueName(@Nullable String selectedUniqueName) {
    if (selectedUniqueName != null) {
      for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
        if (selectedUniqueName.equals(getUniqueName(each))) {
          return each.getConfiguration().getUniqueID();
        }
      }
    }
    return null;
  }

  // used by MPS, don't delete
  public void clearAll() {
    clear();
    myTypesByName.clear();
    initializeConfigurationTypes(new ConfigurationType[0]);
  }

  private void clear() {
    final List<RunnerAndConfigurationSettings> configurations = new ArrayList<RunnerAndConfigurationSettings>(myConfigurations.values());
    myConfigurations.clear();
    myUnknownElements = null;
    myConfigurationToBeforeTasksMap.clear();
    mySharedConfigurations.clear();
    myTemplateConfigurationsMap.clear();
    mySelectedConfigurationId = null;
    myLoadedSelectedConfigurationUniqueName = null;
    myIdToIcon.clear();
    fireRunConfigurationsRemoved(configurations);
  }

  @Nullable
  public RunnerAndConfigurationSettings loadConfiguration(final Element element, boolean isShared) throws InvalidDataException {
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this);
    settings.readExternal(element);
    ConfigurationFactory factory = settings.getFactory();
    if (factory == null) {
      return null;
    }

    final Element methodsElement = element.getChild(METHOD);
    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> map = readStepsBeforeRun(methodsElement, settings);
    if (settings.isTemplate()) {
      myTemplateConfigurationsMap.put(factory.getType().getId() + "." + factory.getName(), settings);
      setBeforeRunTasks(settings.getConfiguration(), map);
    }
    else {
      addConfiguration(settings, isShared, map);
      if (Boolean.valueOf(element.getAttributeValue(SELECTED_ATTR)).booleanValue()) { //to support old style
        setSelectedConfiguration(settings);
      }
    }
    return settings;
  }

  @NotNull
  private Map<Key<? extends BeforeRunTask>, BeforeRunTask> readStepsBeforeRun(final Element child,
                                                                              RunnerAndConfigurationSettings settings) {
    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> map = new HashMap<Key<? extends BeforeRunTask>, BeforeRunTask>();
    if (child != null) {
      for (Object o : child.getChildren(OPTION)) {
        final Element methodElement = (Element)o;
        final String providerName = methodElement.getAttributeValue(NAME_ATTR);
        final Key<? extends BeforeRunTask> id = getProviderKey(providerName);
        final BeforeRunTaskProvider provider = getProvider(id);
        final BeforeRunTask beforeRunTask = provider.createTask(settings.getConfiguration());
        if (beforeRunTask != null) {
          beforeRunTask.readExternal(methodElement);
          map.put(id, beforeRunTask);
        }
      }
    }
    return map;
  }


  @Nullable
  public ConfigurationType getConfigurationType(final String typeName) {
    return myTypesByName.get(typeName);
  }

  @Nullable
  public ConfigurationFactory getFactory(final String typeName, String factoryName) {
    final ConfigurationType type = myTypesByName.get(typeName);
    if (factoryName == null) {
      factoryName = type != null ? type.getConfigurationFactories()[0].getName() : null;
    }
    return findFactoryOfTypeNameByName(typeName, factoryName);
  }


  @Nullable
  private ConfigurationFactory findFactoryOfTypeNameByName(final String typeName, final String factoryName) {
    ConfigurationType type = myTypesByName.get(typeName);
    if (type == null) {
      type = myTypesByName.get(UnknownConfigurationType.NAME);
    }

    return findFactoryOfTypeByName(type, factoryName);
  }

  @Nullable
  private static ConfigurationFactory findFactoryOfTypeByName(final ConfigurationType type, final String factoryName) {
    if (factoryName == null) return null;

    if (type instanceof UnknownConfigurationType) {
      return type.getConfigurationFactories()[0];
    }

    final ConfigurationFactory[] factories = type.getConfigurationFactories();
    for (final ConfigurationFactory factory : factories) {
      if (factoryName.equals(factory.getName())) return factory;
    }

    return null;
  }

  @NotNull
  public String getComponentName() {
    return "RunManager";
  }

  public void setTemporaryConfiguration(@Nullable final RunnerAndConfigurationSettings tempConfiguration) {
    if (tempConfiguration == null) return;

    tempConfiguration.setTemporary(true);
    invalidateConfigurationIcon(tempConfiguration);

    addConfiguration(tempConfiguration, isConfigurationShared(tempConfiguration),
                     getBeforeRunTasks(tempConfiguration.getConfiguration()));
    setActiveConfiguration(tempConfiguration);
  }

  public static boolean isEditBeforeRun(@NotNull final RunnerAndConfigurationSettings configuration) {
    return configuration.isEditBeforeRun();
  }

  public void setEditBeforeRun(@NotNull final RunConfiguration configuration, final boolean edit) {
    final RunnerAndConfigurationSettings settings = getSettings(configuration);
    if (settings != null) settings.setEditBeforeRun(edit);
  }

  Collection<RunnerAndConfigurationSettings> getStableConfigurations() {
    final Map<Integer, RunnerAndConfigurationSettings> result =
      new LinkedHashMap<Integer, RunnerAndConfigurationSettings>(myConfigurations);
    for (Iterator<Map.Entry<Integer, RunnerAndConfigurationSettings>> it = result.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Integer, RunnerAndConfigurationSettings> entry = it.next();
      if (entry.getValue().isTemporary()) {
        it.remove();
      }
    }
    return result.values();
  }

  public boolean isTemporary(@NotNull final RunConfiguration configuration) {
    return Arrays.asList(getTempConfigurations()).contains(configuration);
  }

  public boolean isTemporary(@NotNull RunnerAndConfigurationSettings settings) {
    return settings.isTemporary();
  }

  @NotNull
  public RunConfiguration[] getTempConfigurations() {
    List<RunConfiguration> configurations =
      ContainerUtil.mapNotNull(myConfigurations.values(), new NullableFunction<RunnerAndConfigurationSettings, RunConfiguration>() {
        public RunConfiguration fun(RunnerAndConfigurationSettings settings) {
          return settings.isTemporary() ? settings.getConfiguration() : null;
        }
      });
    return configurations.toArray(new RunConfiguration[configurations.size()]);
  }

  public void makeStable(@Nullable RunConfiguration configuration) {
    RunnerAndConfigurationSettings settings = getSettings(configuration);
    if (settings != null) {
      invalidateConfigurationIcon(settings);
      settings.setTemporary(false);
      fireRunConfigurationChanged(settings);
    }
  }

  @NotNull
  public RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type) {
    return createConfiguration(name, type);
  }

  public boolean isConfigurationShared(final RunnerAndConfigurationSettings settings) {
    Boolean shared = mySharedConfigurations.get(settings.getConfiguration().getUniqueID());
    if (shared == null) {
      final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
      shared = mySharedConfigurations.get(template.getConfiguration().getUniqueID());
    }
    return shared != null && shared.booleanValue();
  }

  public <T extends BeforeRunTask> Collection<T> getBeforeRunTasks(Key<T> taskProviderID, boolean includeOnlyActiveTasks) {
    final Collection<T> tasks = new ArrayList<T>();
    if (includeOnlyActiveTasks) {
      final Set<RunnerAndConfigurationSettings> checkedTemplates = new HashSet<RunnerAndConfigurationSettings>();
      for (RunnerAndConfigurationSettings settings : myConfigurations.values()) {
        final BeforeRunTask runTask = getBeforeRunTask(settings.getConfiguration(), taskProviderID);
        if (runTask != null && runTask.isEnabled()) {
          tasks.add((T)runTask);
        }
        else {
          final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
          if (!checkedTemplates.contains(template)) {
            checkedTemplates.add(template);
            final BeforeRunTask templateTask = getBeforeRunTask(template.getConfiguration(), taskProviderID);
            if (templateTask != null && templateTask.isEnabled()) {
              tasks.add((T)templateTask);
            }
          }
        }
      }
    }
    else {
      for (RunnerAndConfigurationSettings settings : myTemplateConfigurationsMap.values()) {
        final T task = getBeforeRunTask(settings.getConfiguration(), taskProviderID);
        if (task != null) {
          tasks.add(task);
        }
      }
      for (RunnerAndConfigurationSettings settings : myConfigurations.values()) {
        final T task = getBeforeRunTask(settings.getConfiguration(), taskProviderID);
        if (task != null) {
          tasks.add(task);
        }
      }
    }
    return tasks;
  }

  public void invalidateConfigurationIcon(@NotNull final RunnerAndConfigurationSettings settings) {
    myIdToIcon.remove(settings.getConfiguration().getUniqueID());
  }

  public Icon getConfigurationIcon(@NotNull final RunnerAndConfigurationSettings settings) {
    final int uniqueID = settings.getConfiguration().getUniqueID();
    Icon icon = myIdToIcon.get(uniqueID);
    if (icon == null) {
      icon = IconDeferrer.getInstance().defer(settings.getConfiguration().getIcon(), Pair.create(myProject, settings),
                                              new Function<Pair<Project, RunnerAndConfigurationSettings>, Icon>() {
                                                @Override
                                                public Icon fun(Pair<Project, RunnerAndConfigurationSettings> projectRunnerAndConfigurationSettingsPair) {
                                                  if (myProject.isDisposed()) return null;

                                                  Icon icon;
                                                  try {
                                                    settings.checkSettings();
                                                    icon = ProgramRunnerUtil.getConfigurationIcon(myProject, settings, false);
                                                  }
                                                  catch (RuntimeConfigurationException e) {
                                                    icon = ProgramRunnerUtil.getConfigurationIcon(myProject, settings, true);
                                                  }

                                                  return icon;
                                                }
                                              });

      myIdToIcon.put(uniqueID, icon);
    }

    return icon;
  }

  @Override
  @Nullable
  public RunnerAndConfigurationSettings findConfigurationByName(@Nullable String name) {
    if (name == null) return null;
    for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
      if (name.equals(each.getName())) return each;
    }
    return null;
  }

  @Nullable
  public <T extends BeforeRunTask> T getBeforeRunTask(RunConfiguration settings, Key<T> taskProviderID) {
    Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks = myConfigurationToBeforeTasksMap.get(settings);
    if (tasks == null) {
      tasks = getBeforeRunTasks(settings);
      myConfigurationToBeforeTasksMap.put(settings, tasks);
    }
    return (T)tasks.get(taskProviderID);
  }

  public Map<Key<? extends BeforeRunTask>, BeforeRunTask> getBeforeRunTasks(final RunConfiguration settings) {
    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks = myConfigurationToBeforeTasksMap.get(settings);
    if (tasks != null) {
      final Map<Key<? extends BeforeRunTask>, BeforeRunTask> _tasks = new HashMap<Key<? extends BeforeRunTask>, BeforeRunTask>();
      for (Map.Entry<Key<? extends BeforeRunTask>, BeforeRunTask> entry : tasks.entrySet()) {
        _tasks.put(entry.getKey(), entry.getValue().clone());
      }
      return _tasks;
    }

    final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> templateTasks = myConfigurationToBeforeTasksMap.get(template.getConfiguration());
    if (templateTasks != null) {
      final Map<Key<? extends BeforeRunTask>, BeforeRunTask> _tasks = new HashMap<Key<? extends BeforeRunTask>, BeforeRunTask>();
      for (Map.Entry<Key<? extends BeforeRunTask>, BeforeRunTask> entry : templateTasks.entrySet()) {
        _tasks.put(entry.getKey(), entry.getValue().clone());
      }
      return _tasks;
    }

    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> _tasks = new HashMap<Key<? extends BeforeRunTask>, BeforeRunTask>();
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : Extensions
      .getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
      BeforeRunTask task = provider.createTask(settings);
      if (task != null) {
        Key<? extends BeforeRunTask> providerID = provider.getId();
        _tasks.put(providerID, task);
        settings.getFactory().configureBeforeRunTaskDefaults(providerID, task);
      }
    }
    return _tasks;
  }

  public void shareConfiguration(final RunConfiguration runConfiguration, final boolean shareConfiguration) {
    RunnerAndConfigurationSettings settings = getSettings(runConfiguration);
    boolean shouldFire = settings != null && isConfigurationShared(settings) != shareConfiguration;

    if (shareConfiguration && isTemporary(runConfiguration)) makeStable(runConfiguration);
    mySharedConfigurations.put(runConfiguration.getUniqueID(), shareConfiguration);

    if (shouldFire) fireRunConfigurationChanged(settings);
  }

  public final void setBeforeRunTasks(final RunConfiguration runConfiguration, Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks) {
    final Map<Key<? extends BeforeRunTask>, BeforeRunTask> taskMap = getBeforeRunTasks(runConfiguration);
    for (Map.Entry<Key<? extends BeforeRunTask>, BeforeRunTask> entry : tasks.entrySet()) {
      //if (taskMap.containsKey(entry.getKey())) {
      taskMap.put(entry.getKey(), entry.getValue());
      //}
    }
    myConfigurationToBeforeTasksMap.put(runConfiguration, taskMap);
    fireBeforeRunTasksUpdated();
  }

  public final void resetBeforeRunTasks(final RunConfiguration runConfiguration) {
    myConfigurationToBeforeTasksMap.remove(runConfiguration);
    fireBeforeRunTasksUpdated();
  }

  public void addConfiguration(final RunnerAndConfigurationSettings settings, final boolean isShared) {
    addConfiguration(settings, isShared, Collections.<Key<? extends BeforeRunTask>, BeforeRunTask>emptyMap());
  }

  public static RunManagerImpl getInstanceImpl(final Project project) {
    return (RunManagerImpl)RunManager.getInstance(project);
  }

  void removeNotExistingSharedConfigurations(final Set<String> existing) {
    List<RunnerAndConfigurationSettings> removed = new ArrayList<RunnerAndConfigurationSettings>();
    for (Iterator<Map.Entry<Integer, RunnerAndConfigurationSettings>> it = myConfigurations.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Integer, RunnerAndConfigurationSettings> c = it.next();
      final RunnerAndConfigurationSettings o = c.getValue();
      if (!o.isTemplate() && isConfigurationShared(o) && !existing.contains(getUniqueName(o))) {
        removed.add(o);
        invalidateConfigurationIcon(o);
        it.remove();
      }
    }
    fireRunConfigurationsRemoved(removed);
  }

  public void fireRunConfigurationChanged(RunnerAndConfigurationSettings settings) {
    invalidateConfigurationIcon(settings);
    myDispatcher.getMulticaster().runConfigurationChanged(settings);
  }

  private void fireRunConfigurationsRemoved(List<RunnerAndConfigurationSettings> removed) {
    for (RunnerAndConfigurationSettings settings : removed) {
      myDispatcher.getMulticaster().runConfigurationRemoved(settings);
    }
  }

  private void fireRunConfigurationSelected() {
    myDispatcher.getMulticaster().runConfigurationSelected();
  }

  @Override
  public void addRunManagerListener(RunManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeRunManagerListener(RunManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void fireBeforeRunTasksUpdated() {
    myDispatcher.getMulticaster().beforeRunTasksChanged();
  }

  private Map<Key<? extends BeforeRunTask>, BeforeRunTaskProvider> myBeforeStepsMap;
  private Map<String, Key<? extends BeforeRunTask>> myProviderKeysMap;

  @NotNull
  private synchronized BeforeRunTaskProvider getProvider(Key<? extends BeforeRunTask> providerId) {
    if (myBeforeStepsMap == null) {
      initProviderMaps();
    }
    return myBeforeStepsMap.get(providerId);
  }

  @NotNull
  private synchronized Key<? extends BeforeRunTask> getProviderKey(String keyString) {
    if (myProviderKeysMap == null) {
      initProviderMaps();
    }
    Key<? extends BeforeRunTask> id = myProviderKeysMap.get(keyString);
    if (id == null) {
      final UnknownBeforeRunTaskProvider provider = new UnknownBeforeRunTaskProvider(keyString);
      id = provider.getId();
      myProviderKeysMap.put(keyString, id);
      myBeforeStepsMap.put(id, provider);
    }
    return id;
  }

  private void initProviderMaps() {
    myBeforeStepsMap = new HashMap<Key<? extends BeforeRunTask>, BeforeRunTaskProvider>();
    myProviderKeysMap = new HashMap<String, Key<? extends BeforeRunTask>>();
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : Extensions
      .getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
      final Key<? extends BeforeRunTask> id = provider.getId();
      myBeforeStepsMap.put(id, provider);
      myProviderKeysMap.put(id.toString(), id);
    }
  }
}
