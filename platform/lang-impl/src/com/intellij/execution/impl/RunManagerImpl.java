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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector;
import com.intellij.openapi.util.*;
import com.intellij.ui.IconDeferrer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.WeakHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;


public class RunManagerImpl extends RunManagerEx implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunManagerImpl");
  private final Project myProject;

  private final Map<String, ConfigurationType> myTypesByName = new LinkedHashMap<String, ConfigurationType>();

  private final Map<String, RunnerAndConfigurationSettings> myTemplateConfigurationsMap =
    new HashMap<String, RunnerAndConfigurationSettings>();
  private final Map<String, RunnerAndConfigurationSettings> myConfigurations =
    new LinkedHashMap<String, RunnerAndConfigurationSettings>(); // template configurations are not included here
  private final Map<String, Boolean> mySharedConfigurations = new TreeMap<String, Boolean>();
  private final Map<RunConfiguration, List<BeforeRunTask>> myConfigurationToBeforeTasksMap = new WeakHashMap<RunConfiguration, List<BeforeRunTask>>();

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  @Nullable private String myLoadedSelectedConfigurationUniqueName = null;
  @Nullable private String mySelectedConfigurationId = null;

  private Map<String, Icon> myIdToIcon = new HashMap<String, Icon>();

  @NonNls
  protected static final String CONFIGURATION = "configuration";
  protected static final String RECENT = "recent_temporary";
  private ConfigurationType[] myTypes;
  private final RunManagerConfig myConfig;
  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String SELECTED_ATTR = "selected";
  @NonNls private static final String METHOD = "method";
  @NonNls private static final String OPTION = "option";

  private List<Element> myUnknownElements = null;
  private JDOMExternalizableStringList myOrder = new JDOMExternalizableStringList();
  private final ArrayList<RunConfiguration> myRecentlyUsedTemporaries = new ArrayList<RunConfiguration>();
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
      @Override
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

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void projectOpened() {
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings createConfiguration(@NotNull final String name, @NotNull final ConfigurationFactory factory) {
    return createConfiguration(doCreateConfiguration(name, factory, true), factory);
  }

  protected RunConfiguration doCreateConfiguration(@NotNull String name, @NotNull ConfigurationFactory factory, final boolean fromTemplate) {
    if (fromTemplate) {
      return factory.createConfiguration(name, getConfigurationTemplate(factory).getConfiguration());
    }
    else {
      final RunConfiguration configuration = factory.createTemplateConfiguration(myProject, this);
      configuration.setName(name);
      return configuration;
    }
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings createConfiguration(@NotNull final RunConfiguration runConfiguration,
                                                            @NotNull final ConfigurationFactory factory) {
    RunnerAndConfigurationSettings template = getConfigurationTemplate(factory);
    myConfigurationToBeforeTasksMap.put(runConfiguration, getBeforeRunTasks(template.getConfiguration()));
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this, runConfiguration, false);
    settings.importRunnerAndConfigurationSettings((RunnerAndConfigurationSettingsImpl)template);
    if (!mySharedConfigurations.containsKey(settings.getUniqueID())) {
      shareConfiguration(settings, isConfigurationShared(template));
    }
    return settings;
  }

  @Override
  public void projectClosed() {
    myTemplateConfigurationsMap.clear();
  }

  @Override
  public RunManagerConfig getConfig() {
    return myConfig;
  }

  @Override
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
  @Override
  @NotNull
  public List<RunConfiguration> getConfigurationsList(@NotNull final ConfigurationType type) {

    final List<RunConfiguration> list = new ArrayList<RunConfiguration>();
    for (RunnerAndConfigurationSettings myConfiguration : getSortedConfigurations()) {
      final RunConfiguration configuration = myConfiguration.getConfiguration();
      final ConfigurationType configurationType = configuration.getType();
      if (type.getId().equals(configurationType.getId())) {
        list.add(configuration);
      }
    }
    return Collections.unmodifiableList(list);
  }

  @Override
  @NotNull
  public List<RunConfiguration> getAllConfigurationsList() {
    List<RunConfiguration> result = new ArrayList<RunConfiguration>(myConfigurations.size());
    for (Iterator<RunnerAndConfigurationSettings> iterator = getSortedConfigurations().iterator(); iterator.hasNext();) {
      RunnerAndConfigurationSettings settings = iterator.next();
      result.add(settings.getConfiguration());
    }
    return Collections.unmodifiableList(result);
  }

  @NotNull
  @Override
  public RunConfiguration[] getAllConfigurations() {
    List<RunConfiguration> list = getAllConfigurationsList();
    return list.toArray(new RunConfiguration[list.size()]);
  }

  @NotNull
  @Override
  public List<RunnerAndConfigurationSettings> getAllSettings() {
    return Collections.unmodifiableList(new ArrayList<RunnerAndConfigurationSettings>(getSortedConfigurations()));
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
  @Override
  @NotNull
  public List<RunnerAndConfigurationSettings> getConfigurationSettingsList(@NotNull final ConfigurationType type) {

    final LinkedHashSet<RunnerAndConfigurationSettings> set = new LinkedHashSet<RunnerAndConfigurationSettings>();
    for (RunnerAndConfigurationSettings configuration : getSortedConfigurations()) {
      final ConfigurationType configurationType = configuration.getType();
      if (configurationType != null && type.getId().equals(configurationType.getId())) {
        set.add(configuration);
      }
    }
    return Collections.unmodifiableList(new ArrayList<RunnerAndConfigurationSettings>(set));
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull ConfigurationType type) {
    List<RunnerAndConfigurationSettings> list = getConfigurationSettingsList(type);
    return list.toArray(new RunnerAndConfigurationSettings[list.size()]);
  }

  @NotNull
  @Override
  public RunConfiguration[] getConfigurations(@NotNull ConfigurationType type) {
    RunnerAndConfigurationSettings[] settings = getConfigurationSettings(type);
    RunConfiguration[] result = new RunConfiguration[settings.length];
    for (int i = 0; i < settings.length; i++) {
      result[i] = settings[i].getConfiguration();
    }
    return result;
  }

  @NotNull
  @Override
  public Map<String, List<RunnerAndConfigurationSettings>> getStructure(@NotNull ConfigurationType type) {
    LinkedHashMap<String, List<RunnerAndConfigurationSettings>> map = new LinkedHashMap<String, List<RunnerAndConfigurationSettings>>();
    List<RunnerAndConfigurationSettings> typeList = new ArrayList<RunnerAndConfigurationSettings>();
    List<RunnerAndConfigurationSettings> settings = getConfigurationSettingsList(type);
    for (RunnerAndConfigurationSettings setting : settings) {
      String folderName = setting.getFolderName();
      if (folderName == null) {
        typeList.add(setting);
      }
      else {
        List<RunnerAndConfigurationSettings> list = map.get(folderName);
        if (list == null) {
          map.put(folderName, list = new ArrayList<RunnerAndConfigurationSettings>());
        }
        list.add(setting);
      }
    }
    LinkedHashMap<String, List<RunnerAndConfigurationSettings>> result = new LinkedHashMap<String, List<RunnerAndConfigurationSettings>>();
    for (Map.Entry<String, List<RunnerAndConfigurationSettings>> entry : map.entrySet()) {
      result.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }
    result.put(null, Collections.unmodifiableList(typeList));
    return Collections.unmodifiableMap(result);
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings getConfigurationTemplate(final ConfigurationFactory factory) {
    RunnerAndConfigurationSettings template = myTemplateConfigurationsMap.get(factory.getType().getId() + "." + factory.getName());
    if (template == null) {
      template = new RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(myProject, this), true);
      template.setSingleton(factory.isConfigurationSingletonByDefault());
      if (template.getConfiguration() instanceof UnknownRunConfiguration) {
        ((UnknownRunConfiguration)template.getConfiguration()).setDoNotStore(true);
      }
      myTemplateConfigurationsMap.put(factory.getType().getId() + "." + factory.getName(), template);
    }
    return template;
  }

  @Override
  public void addConfiguration(RunnerAndConfigurationSettings settings,
                               boolean shared,
                               List<BeforeRunTask> tasks, boolean addEnabledTemplateTasksIfAbsent) {
    final RunConfiguration configuration = settings.getConfiguration();

    String existingId = findExistingConfigurationId(settings);
    String newId = settings.getUniqueID();
    RunnerAndConfigurationSettings existingSettings = null;

    if (existingId != null) {
      existingSettings = myConfigurations.remove(existingId);
      mySharedConfigurations.remove(existingId);
    }

    if (mySelectedConfigurationId != null && mySelectedConfigurationId.equals(existingId)) {
      setSelectedConfigurationId(newId);
    }
    myConfigurations.put(newId, settings);
    if (existingId == null) {
      refreshUsagesList(configuration);
    }
    checkRecentsLimit();

    mySharedConfigurations.put(newId, shared);
    setBeforeRunTasks(configuration, tasks, addEnabledTemplateTasksIfAbsent);

    if (existingSettings == settings) {
      myDispatcher.getMulticaster().runConfigurationChanged(settings);
    }
    else {
      myDispatcher.getMulticaster().runConfigurationAdded(settings);
    }
  }

  @Override
  public void refreshUsagesList(RunProfile profile) {
    if (!(profile instanceof RunConfiguration)) return;
    RunnerAndConfigurationSettings settings = getSettings((RunConfiguration)profile);
    if (settings != null && settings.isTemporary()) {
      myRecentlyUsedTemporaries.remove((RunConfiguration)profile);
      myRecentlyUsedTemporaries.add(0, (RunConfiguration)profile);
      trimUsagesListToLimit();
    }
  }

  private void trimUsagesListToLimit() {
    while(myRecentlyUsedTemporaries.size() > getConfig().getRecentsLimit()) {
      myRecentlyUsedTemporaries.remove(myRecentlyUsedTemporaries.size() - 1);
    }
  }

  void checkRecentsLimit() {
    trimUsagesListToLimit();
    List<RunnerAndConfigurationSettings> removed = new ArrayList<RunnerAndConfigurationSettings>();
    while (getTempConfigurationsList().size() > getConfig().getRecentsLimit()) {
      for (Iterator<Map.Entry<String, RunnerAndConfigurationSettings>> it = myConfigurations.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<String, RunnerAndConfigurationSettings> entry = it.next();
        if (entry.getValue().isTemporary() && !myRecentlyUsedTemporaries.contains(entry.getValue().getConfiguration())) {
          removed.add(entry.getValue());
          it.remove();
          break;
        }
      }
    }
    fireRunConfigurationsRemoved(removed);
  }

  public void setOrdered(boolean ordered) {
    myOrdered = ordered;
  }

  public void saveOrder() {
    myOrder.clear();
    for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
      if (each.getType() instanceof UnknownConfigurationType) continue;
      myOrder.add(each.getUniqueID());
    }
  }

  @Override
  public void removeConfiguration(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) return;

    for (Iterator<RunnerAndConfigurationSettings> it = getSortedConfigurations().iterator(); it.hasNext(); ) {
      final RunnerAndConfigurationSettings configuration = it.next();
      if (configuration.equals(settings)) {
        if (mySelectedConfigurationId != null && mySelectedConfigurationId == settings.getUniqueID()) {
          setSelectedConfiguration(null);
        }

        it.remove();
        mySharedConfigurations.remove(settings.getUniqueID());
        myConfigurationToBeforeTasksMap.remove(settings.getConfiguration());
        myRecentlyUsedTemporaries.remove(settings.getConfiguration());
        invalidateConfigurationIcon(configuration);
        myDispatcher.getMulticaster().runConfigurationRemoved(configuration);
        break;
      }
    }
  }

  @Override
  @Nullable
  public RunnerAndConfigurationSettings getSelectedConfiguration() {
    if (mySelectedConfigurationId == null && myLoadedSelectedConfigurationUniqueName != null) {
      setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName);
    }
    return mySelectedConfigurationId == null ? null : myConfigurations.get(mySelectedConfigurationId);
  }

  @Override
  public void setSelectedConfiguration(@Nullable RunnerAndConfigurationSettings settings) {
    setSelectedConfigurationId(settings == null ? null : settings.getUniqueID());
    if (settings != null) {
      invalidateConfigurationIcon(settings);
    }
    fireRunConfigurationSelected();
  }

  private void setSelectedConfigurationId(@Nullable String id) {
    mySelectedConfigurationId = id;
    if (mySelectedConfigurationId != null) myLoadedSelectedConfigurationUniqueName = null;
  }

  @Override
  @NotNull
  public Collection<RunnerAndConfigurationSettings> getSortedConfigurations() {
    if (!myOrdered) { //compatibility
      List<Pair<String, RunnerAndConfigurationSettings>> order
        = new ArrayList<Pair<String, RunnerAndConfigurationSettings>>(myConfigurations.size());
      final List<String> folderNames = new ArrayList<String>();
      for (RunnerAndConfigurationSettings each : myConfigurations.values()) {
        order.add(Pair.create(each.getUniqueID(), each));
        String folderName = each.getFolderName();
        if (folderName != null && !folderNames.contains(folderName)) {
          folderNames.add(folderName);
        }
      }
      folderNames.add(null);
      myConfigurations.clear();

      if (myOrder.isEmpty()) {
        // IDEA-63663 Sort run configurations alphabetically if clean checkout 
        Collections.sort(order, new Comparator<Pair<String, RunnerAndConfigurationSettings>>() {
          @Override
          public int compare(Pair<String, RunnerAndConfigurationSettings> o1, Pair<String, RunnerAndConfigurationSettings> o2) {
            boolean temporary1 = o1.getSecond().isTemporary();
            boolean temporary2 = o2.getSecond().isTemporary();
            if (temporary1 == temporary2) {
              return o1.first.compareTo(o2.first);
            } else {
              return temporary1 ? 1 : -1;
            }
          }
        });
      }
      else {
        Collections.sort(order, new Comparator<Pair<String, RunnerAndConfigurationSettings>>() {
          @Override
          public int compare(Pair<String, RunnerAndConfigurationSettings> o1, Pair<String, RunnerAndConfigurationSettings> o2) {
            int i1 = folderNames.indexOf(o1.getSecond().getFolderName());
            int i2 = folderNames.indexOf(o2.getSecond().getFolderName());
            if (i1 != i2) {
              return i1 - i2;
            }
            boolean temporary1 = o1.getSecond().isTemporary();
            boolean temporary2 = o2.getSecond().isTemporary();
            if (temporary1 == temporary2) {
              int index1 = myOrder.indexOf(o1.first);
              int index2 = myOrder.indexOf(o2.first);
              if (index1 ==-1 && index2 ==-1) {
                return o1.second.getName().compareTo(o2.second.getName());
              }
              return index1 - index2;
            } else {
              return temporary1 ? 1 : -1;
            }
          }
        });
      }

      for (Pair<String, RunnerAndConfigurationSettings> each : order) {
        RunnerAndConfigurationSettings setting = each.second;
        myConfigurations.put(setting.getUniqueID(), setting);
      }

      myOrdered = true;
    }
    return myConfigurations.values();
  }

  public static boolean canRunConfiguration(@NotNull final RunnerAndConfigurationSettings configuration,
                                            @NotNull final Executor executor) {
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

  @Override
  public void writeExternal(@NotNull final Element parentNode) throws WriteExternalException {
    writeContext(parentNode);//writes temporary configurations here
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
      order.add(each.getUniqueID());
    }

    order.writeExternal(parentNode);

    final JDOMExternalizableStringList recentList = new JDOMExternalizableStringList();
    for (RunConfiguration each : myRecentlyUsedTemporaries) {
      if (each.getType() instanceof UnknownConfigurationType) continue;
      RunnerAndConfigurationSettings settings = getSettings(each);
      if (settings == null) {
        continue;
      }
      recentList.add(settings.getUniqueID());
    }
    if (!recentList.isEmpty()) {
      final Element recent = new Element(RECENT);
      parentNode.addContent(recent);
      recentList.writeExternal(recent);
    }

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
      parentNode.setAttribute(SELECTED_ATTR, selected.getUniqueID());
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
      final List<BeforeRunTask> tasks = ContainerUtil.createLockFreeCopyOnWriteList(getBeforeRunTasks(settings.getConfiguration()));
      final Element methodsElement = new Element(METHOD);
      Map<Key<BeforeRunTask>, BeforeRunTask> templateTasks = new HashMap<Key<BeforeRunTask>, BeforeRunTask>();
      List<BeforeRunTask> beforeRunTasks = settings.isTemplate()
                                           ? getHardcodedBeforeRunTasks(settings.getConfiguration())
                                           : getBeforeRunTasks(getConfigurationTemplate(settings.getFactory()).getConfiguration());
      for (BeforeRunTask templateTask : beforeRunTasks) {
        templateTasks.put(templateTask.getProviderId(), templateTask);
        if (templateTask.isEnabled()) {
          boolean found = false;
          for (BeforeRunTask realTask : tasks) {
            if (realTask.getProviderId() == templateTask.getProviderId()) {
              found = true;
              break;
            }
          }
          if (!found) {
            BeforeRunTask clone = templateTask.clone();
            clone.setEnabled(false);
            tasks.add(0, clone);
          }
        }
      }
      for (int i = 0, size = tasks.size(); i < size; i++) {
        BeforeRunTask task = tasks.get(i);
        int j = 0;
        BeforeRunTask templateTask = null;
        for (Map.Entry<Key<BeforeRunTask>, BeforeRunTask> entry : templateTasks.entrySet()) {
          if (entry.getKey() == task.getProviderId()) {
            templateTask = entry.getValue();
            break;
          }
          j++;
        }
        if (task.equals(templateTask) && i == j) {
          continue; // not neccesary saving if the task is the same as template and on the same place
        }
        final Element child = new Element(OPTION);
        child.setAttribute(NAME_ATTR, task.getProviderId().toString());
        task.writeExternal(child);
        methodsElement.addContent(child);
      }
      configurationElement.addContent(methodsElement);
    }
  }

  @Override
  public void readExternal(final Element parentNode) throws InvalidDataException {
    clear();

    final Comparator<Element> comparator = new Comparator<Element>() {
      @Override
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
    //Begin migration (old ids to UUIDs)
    for (int i = 0; i < myOrder.size(); i++) {
      String id = myOrder.get(i);
      for (RunnerAndConfigurationSettings settings : myConfigurations.values()) {
        RunConfiguration configuration = settings.getConfiguration();
        if (configuration != null && id.equals(configuration.getType().getDisplayName() + "." + configuration.getName() +
                                               (configuration instanceof UnknownRunConfiguration ? configuration.getUniqueID() : ""))) {
          myOrder.set(i, settings.getUniqueID());
          break;
        }
      }
    }
    //End migration
    myRecentlyUsedTemporaries.clear();
    Element recentNode = parentNode.getChild(RECENT);
    if (recentNode != null) {
      JDOMExternalizableStringList list = new JDOMExternalizableStringList();
      list.readExternal(recentNode);
      for (int i = 0; i < list.size(); i++) {
        String id = list.get(i);
        for (RunnerAndConfigurationSettings settings : myConfigurations.values()) {
          RunConfiguration configuration = settings.getConfiguration();
          if (configuration != null && id.equals(configuration.getType().getDisplayName() + "." + configuration.getName() +
                                                 (configuration instanceof UnknownRunConfiguration ? configuration.getUniqueID() : ""))) {
            list.set(i, settings.getUniqueID());
            break;
          }
        }
      }
      for (String name : list) {
        RunnerAndConfigurationSettings settings = myConfigurations.get(name);
        if (settings != null)
          myRecentlyUsedTemporaries.add(settings.getConfiguration());
      }
    }
    myOrdered = false;

    myLoadedSelectedConfigurationUniqueName = parentNode.getAttributeValue(SELECTED_ATTR);
    setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName);

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
        myLoadedSelectedConfigurationUniqueName = config.getUniqueID();
      }
    }

    setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName);

    fireRunConfigurationSelected();
  }

  @Nullable
  private String findExistingConfigurationId(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null) {
      for (Map.Entry<String, RunnerAndConfigurationSettings> entry : myConfigurations.entrySet()) {
        if (entry.getValue() == settings) {
          return entry.getKey();
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
    myRecentlyUsedTemporaries.clear();
    fireRunConfigurationsRemoved(configurations);
  }

  @Nullable
  public RunnerAndConfigurationSettings loadConfiguration(final Element element, boolean isShared) throws InvalidDataException {
    final RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this);
    settings.readExternal(element);
    ConfigurationFactory factory = settings.getFactory();
    if (factory == null) {
      return null;
    }

    final Element methodsElement = element.getChild(METHOD);
    final List<BeforeRunTask> tasks = readStepsBeforeRun(methodsElement, settings);
    if (settings.isTemplate()) {
      myTemplateConfigurationsMap.put(factory.getType().getId() + "." + factory.getName(), settings);
      setBeforeRunTasks(settings.getConfiguration(), tasks, true);
    }
    else {
      addConfiguration(settings, isShared, tasks, true);
      if (Boolean.valueOf(element.getAttributeValue(SELECTED_ATTR)).booleanValue()) { //to support old style
        setSelectedConfiguration(settings);
      }
    }
    return settings;
  }

  @NotNull
  private List<BeforeRunTask> readStepsBeforeRun(final Element child, RunnerAndConfigurationSettings settings) {
    final List<BeforeRunTask> result = new ArrayList<BeforeRunTask>();
    if (child != null) {
      for (Object o : child.getChildren(OPTION)) {
        final Element methodElement = (Element)o;
        final String providerName = methodElement.getAttributeValue(NAME_ATTR);
        final Key<? extends BeforeRunTask> id = getProviderKey(providerName);
        final BeforeRunTaskProvider provider = getProvider(id);
        final BeforeRunTask beforeRunTask = provider.createTask(settings.getConfiguration());
        if (beforeRunTask != null) {
          beforeRunTask.readExternal(methodElement);
          result.add(beforeRunTask);
        }
      }
    }
    return result;
  }


  @Nullable
  public ConfigurationType getConfigurationType(final String typeName) {
    return myTypesByName.get(typeName);
  }

  @Nullable
  public ConfigurationFactory getFactory(final String typeName, String factoryName) {
    return getFactory(typeName, factoryName, false);
  }

  @Nullable
  public ConfigurationFactory getFactory(final String typeName, String factoryName, boolean checkUnknown) {
    final ConfigurationType type = myTypesByName.get(typeName);
    if (type == null && checkUnknown && typeName != null) {
      UnknownFeaturesCollector.getInstance(myProject).registerUnknownRunConfiguration(typeName);
    }
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

  @Override
  @NotNull
  public String getComponentName() {
    return "RunManager";
  }

  @Override
  public void setTemporaryConfiguration(@Nullable final RunnerAndConfigurationSettings tempConfiguration) {
    if (tempConfiguration == null) return;

    tempConfiguration.setTemporary(true);
    invalidateConfigurationIcon(tempConfiguration);

    addConfiguration(tempConfiguration, isConfigurationShared(tempConfiguration),
                     getBeforeRunTasks(tempConfiguration.getConfiguration()), false);
    setSelectedConfiguration(tempConfiguration);
  }

  Collection<RunnerAndConfigurationSettings> getStableConfigurations() {
    final Map<String, RunnerAndConfigurationSettings> result =
      new LinkedHashMap<String, RunnerAndConfigurationSettings>(myConfigurations);
    for (Iterator<Map.Entry<String, RunnerAndConfigurationSettings>> it = result.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, RunnerAndConfigurationSettings> entry = it.next();
      if (entry.getValue().isTemporary()) {
        it.remove();
      }
    }
    return result.values();
  }

  @NotNull
  Collection<? extends RunnerAndConfigurationSettings> getConfigurationSettings() {
    return myConfigurations.values();
  }

  @Override
  public boolean isTemporary(@NotNull final RunConfiguration configuration) {
    return Arrays.asList(getTempConfigurations()).contains(configuration);
  }

  @Override
  @NotNull
  public List<RunnerAndConfigurationSettings> getTempConfigurationsList() {
    List<RunnerAndConfigurationSettings> configurations =
      ContainerUtil.filter(myConfigurations.values(), new Condition<RunnerAndConfigurationSettings>() {
      public boolean value(RunnerAndConfigurationSettings settings) {
        return settings.isTemporary();
      }
      });
    return Collections.unmodifiableList(configurations);
  }

  @NotNull
  @Override
  public RunConfiguration[] getTempConfigurations() {
    List<RunnerAndConfigurationSettings> list = getTempConfigurationsList();
    RunConfiguration[] result = new RunConfiguration[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = list.get(i).getConfiguration();
    }
    return result;
  }

  public void makeStable(@NotNull RunnerAndConfigurationSettings settings) {
      invalidateConfigurationIcon(settings);
      settings.setTemporary(false);
      myRecentlyUsedTemporaries.remove(settings.getConfiguration());
      if (!myOrder.isEmpty()) {
        setOrdered(false);
      }
      fireRunConfigurationChanged(settings);
  }

  @Override
  public void makeStable(@NotNull RunConfiguration configuration) {
    RunnerAndConfigurationSettings settings = getSettings(configuration);
    if (settings != null) {
      makeStable(settings);
    }
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings createRunConfiguration(@NotNull String name, @NotNull ConfigurationFactory type) {
    return createConfiguration(name, type);
  }

  @Override
  public boolean isConfigurationShared(final RunnerAndConfigurationSettings settings) {
    Boolean shared = mySharedConfigurations.get(settings.getUniqueID());
    if (shared == null) {
      final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
      shared = mySharedConfigurations.get(template.getUniqueID());
    }
    return shared != null && shared.booleanValue();
  }

  @Override
  @NotNull
  public <T extends BeforeRunTask> List<T> getBeforeRunTasks(Key<T> taskProviderID) {
    final List<T> tasks = new ArrayList<T>();
    final List<RunnerAndConfigurationSettings> checkedTemplates = new ArrayList<RunnerAndConfigurationSettings>();
    List<RunnerAndConfigurationSettings> settingsList = new ArrayList<RunnerAndConfigurationSettings>(myConfigurations.values());
    for (RunnerAndConfigurationSettings settings : settingsList) {
      final List<BeforeRunTask> runTasks = getBeforeRunTasks(settings.getConfiguration());
      for (BeforeRunTask task : runTasks) {
        if (task != null && task.isEnabled() && task.getProviderId() == taskProviderID) {
          tasks.add((T)task);
        }
        else {
          final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
          if (!checkedTemplates.contains(template)) {
            checkedTemplates.add(template);
            final List<BeforeRunTask> templateTasks = getBeforeRunTasks(template.getConfiguration());
            for (BeforeRunTask templateTask : templateTasks) {
              if (templateTask != null && templateTask.isEnabled() && templateTask.getProviderId() == taskProviderID) {
                tasks.add((T)templateTask);
              }
            }
          }
        }
      }
    }
    return tasks;
  }

  @Override
  public void invalidateConfigurationIcon(@NotNull final RunnerAndConfigurationSettings settings) {
    myIdToIcon.remove(settings.getUniqueID());
  }

  @Override
  public Icon getConfigurationIcon(@NotNull final RunnerAndConfigurationSettings settings) {
    final String uniqueID = settings.getUniqueID();
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
                                                    icon = ProgramRunnerUtil.getConfigurationIcon(settings, false);
                                                  }
                                                  catch (RuntimeConfigurationException e) {
                                                    icon = ProgramRunnerUtil.getConfigurationIcon(settings, true);
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

  @NotNull
  @Override
  public <T extends BeforeRunTask> List<T> getBeforeRunTasks(RunConfiguration settings, Key<T> taskProviderID) {
    if (settings instanceof WrappingRunConfiguration) {
      return getBeforeRunTasks(((WrappingRunConfiguration)settings).getPeer(), taskProviderID);
    }
    List<BeforeRunTask> tasks = myConfigurationToBeforeTasksMap.get(settings);
    if (tasks == null) {
      tasks = getBeforeRunTasks(settings);
      myConfigurationToBeforeTasksMap.put(settings, tasks);
    }
    List<T> result = new ArrayList<T>();
    for (BeforeRunTask task : tasks) {
      if (task.getProviderId() == taskProviderID)
        result.add((T)task);
    }
    return result;
  }

  @Override
  @NotNull
  public List<BeforeRunTask> getBeforeRunTasks(final RunConfiguration settings) {
    if (settings instanceof WrappingRunConfiguration) {
      return getBeforeRunTasks(((WrappingRunConfiguration)settings).getPeer());
    }
    final List<BeforeRunTask> tasks = myConfigurationToBeforeTasksMap.get(settings);
    if (tasks != null) {
      return getCopies(tasks);
    }
    return getTemplateBeforeRunTasks(settings);
  }

  private List<BeforeRunTask> getTemplateBeforeRunTasks(RunConfiguration settings) {
    final RunnerAndConfigurationSettings template = getConfigurationTemplate(settings.getFactory());
    final List<BeforeRunTask> templateTasks = myConfigurationToBeforeTasksMap.get(template.getConfiguration());
    return templateTasks != null ? getCopies(templateTasks) : getHardcodedBeforeRunTasks(settings);
  }

  private List<BeforeRunTask> getHardcodedBeforeRunTasks(RunConfiguration settings) {
    final List<BeforeRunTask> _tasks = new ArrayList<BeforeRunTask>();
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : Extensions
      .getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
      BeforeRunTask task = provider.createTask(settings);
      if (task != null && task.isEnabled()) {
        Key<? extends BeforeRunTask> providerID = provider.getId();
        settings.getFactory().configureBeforeRunTaskDefaults(providerID, task);
        if (task.isEnabled()) {
          _tasks.add(task);
        }
      }
    }
    return _tasks;
  }

  @NotNull
  private static List<BeforeRunTask> getCopies(List<BeforeRunTask> original) {
    List<BeforeRunTask> result = new ArrayList<BeforeRunTask>();
    if (original != null) {
      for (BeforeRunTask task : original) {
        if (!task.isEnabled())
          continue;
        result.add(task.clone());
      }
    }
    return result;
  }

  public void shareConfiguration(final RunnerAndConfigurationSettings settings, final boolean shareConfiguration) {
    boolean shouldFire = settings != null && isConfigurationShared(settings) != shareConfiguration;

    if (shareConfiguration && settings.isTemporary()) makeStable(settings);
    mySharedConfigurations.put(settings.getUniqueID(), shareConfiguration);

    if (shouldFire) fireRunConfigurationChanged(settings);
  }

  @Override
  public final void setBeforeRunTasks(final RunConfiguration runConfiguration, @NotNull List<BeforeRunTask> tasks, boolean addEnabledTemplateTasksIfAbsent) {
    List<BeforeRunTask> result = new ArrayList<BeforeRunTask>(tasks);
    if (addEnabledTemplateTasksIfAbsent) {
      List<BeforeRunTask> templates = getTemplateBeforeRunTasks(runConfiguration);
      Set<Key<BeforeRunTask>> idsToSet = new HashSet<Key<BeforeRunTask>>();
      for (BeforeRunTask task : tasks) {
        idsToSet.add(task.getProviderId());
      }
      int i = 0;
      for (BeforeRunTask template : templates) {
        if (!idsToSet.contains(template.getProviderId())) {
          result.add(i, template);
          i++;
        }
      }
    }
    myConfigurationToBeforeTasksMap.put(runConfiguration, result.isEmpty() ? Collections.<BeforeRunTask>emptyList() : result);
    fireBeforeRunTasksUpdated();
  }

  public final void resetBeforeRunTasks(final RunConfiguration runConfiguration) {
    myConfigurationToBeforeTasksMap.remove(runConfiguration);
    fireBeforeRunTasksUpdated();
  }

  @Override
  public void addConfiguration(final RunnerAndConfigurationSettings settings, final boolean isShared) {
    addConfiguration(settings, isShared, getTemplateBeforeRunTasks(settings.getConfiguration()), false);
  }

  public static RunManagerImpl getInstanceImpl(final Project project) {
    return (RunManagerImpl)RunManager.getInstance(project);
  }

  void removeNotExistingSharedConfigurations(final Set<String> existing) {
    List<RunnerAndConfigurationSettings> removed = new ArrayList<RunnerAndConfigurationSettings>();
    for (Iterator<Map.Entry<String, RunnerAndConfigurationSettings>> it = myConfigurations.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, RunnerAndConfigurationSettings> entry = it.next();
      final RunnerAndConfigurationSettings settings = entry.getValue();
      if (!settings.isTemplate() && isConfigurationShared(settings) && !existing.contains(settings.getUniqueID())) {
        removed.add(settings);
        invalidateConfigurationIcon(settings);
        it.remove();
      }
    }
    fireRunConfigurationsRemoved(removed);
  }

  public void fireRunConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
    invalidateConfigurationIcon(settings);
    myDispatcher.getMulticaster().runConfigurationChanged(settings);
  }

  private void fireRunConfigurationsRemoved(@NotNull List<RunnerAndConfigurationSettings> removed) {
    myRecentlyUsedTemporaries.removeAll(removed);
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
    myBeforeStepsMap = new LinkedHashMap<Key<? extends BeforeRunTask>, BeforeRunTaskProvider>();
    myProviderKeysMap = new LinkedHashMap<String, Key<? extends BeforeRunTask>>();
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : Extensions
      .getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
      final Key<? extends BeforeRunTask> id = provider.getId();
      myBeforeStepsMap.put(id, provider);
      myProviderKeysMap.put(id.toString(), id);
    }
  }
}
