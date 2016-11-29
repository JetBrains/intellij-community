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
package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ui.wizard.WizardModel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class StartupWizardModel extends WizardModel {
  private final Set<String> myDisabledPluginIds = new HashSet<>();
  private final Map<String, SelectPluginsStep> myStepMap = new HashMap<>();
  private Map<PluginId, SelectPluginsStep> myPluginToStepMap = new HashMap<>();
  private MultiMap<IdeaPluginDescriptor, IdeaPluginDescriptor> myBackwardDependencies =
    new MultiMap<>();
  private SelectPluginsStep myOtherStep;
  private IdeaPluginDescriptor[] myAllPlugins;

  public StartupWizardModel(final List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(ApplicationNamesInfo.getInstance().getFullProductName() + " Initial Configuration Wizard");
    loadDisabledPlugins(new File(PathManager.getConfigPath()));

    for (ApplicationInfoEx.PluginChooserPage page : pluginChooserPages) {
      if (page.getCategory() == null) {
        myOtherStep = new SelectPluginsStep(page.getTitle(), this, null);
      }
      else {
        addSelectPluginsStep(page.getCategory(), page.getTitle(), page.getDependentPlugin());
      }
    }
    if (myOtherStep != null) {
      add(myOtherStep);
    }

    myAllPlugins = PluginManager.loadDescriptors(null, ContainerUtil.<String>newArrayList());
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      if (pluginDescriptor.getPluginId().getIdString().equals("com.intellij")) {
        // skip 'IDEA CORE' plugin
        continue;
      }
      PluginManager.initClassLoader(getClass().getClassLoader(), (IdeaPluginDescriptorImpl) pluginDescriptor);
      SelectPluginsStep step = myStepMap.get(pluginDescriptor.getCategory());
      if (step == null) {
        step = myOtherStep;
      }
      if (step != null) {
        step.addPlugin(pluginDescriptor);
        myPluginToStepMap.put(pluginDescriptor.getPluginId(), step);
        for (PluginId pluginId : pluginDescriptor.getDependentPluginIds()) {
          if (!ArrayUtil.contains(pluginId, pluginDescriptor.getOptionalDependentPluginIds())) {
            IdeaPluginDescriptor dependee = findPlugin(pluginId);
            if (dependee != null) {
              myBackwardDependencies.putValue(dependee, pluginDescriptor);
            }
          }
        }
      }
    }
    for (SelectPluginsStep step : myStepMap.values()) {
      step.fillPlugins();
    }
    if (myOtherStep != null) {
      myOtherStep.fillPlugins();
    }
  }

  @Nullable
  private IdeaPluginDescriptor findPlugin(PluginId pluginId) {
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      if (pluginDescriptor.getPluginId().equals(pluginId)) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  static List<PluginId> getNonOptionalDependencies(final IdeaPluginDescriptor descriptor) {
    List<PluginId> result = new ArrayList<>();
    for (PluginId pluginId : descriptor.getDependentPluginIds()) {
      if (pluginId.getIdString().equals("com.intellij")) continue;
      if (!ArrayUtil.contains(pluginId, descriptor.getOptionalDependentPluginIds())) {
        result.add(pluginId);
      }
    }
    return result;
  }

  private SelectPluginsStep addSelectPluginsStep(final String category, final String title, final String requirePlugin) {
    final SelectPluginsStep step = new SelectPluginsStep(title, this, requirePlugin);
    add(step);
    myStepMap.put(category, step);
    return step;
  }

  public void loadDisabledPlugins(final File configDir) {
    PluginManager.loadDisabledPlugins(configDir.getPath(), myDisabledPluginIds);
  }

  public Collection<String> getDisabledPluginIds() {
    return myDisabledPluginIds;
  }

  public boolean isDisabledPlugin(IdeaPluginDescriptor descriptor) {
    return myDisabledPluginIds.contains(descriptor.getPluginId().toString());
  }

  public void setPluginEnabled(final IdeaPluginDescriptor desc, boolean value) {
    if (value) {
      myDisabledPluginIds.remove(desc.getPluginId().toString());
    }
    else {
      myDisabledPluginIds.add(desc.getPluginId().toString());
    }
  }

  public void setPluginEnabledWithDependencies(final IdeaPluginDescriptor desc) {
    setPluginEnabled(desc, true);
    for(PluginId id: getNonOptionalDependencies(desc)) {
      final IdeaPluginDescriptor dependent = findPlugin(id);
      if (dependent != null) {
        setPluginEnabledWithDependencies(dependent);
      }
    }
  }

  public void setPluginDisabledWithDependents(final IdeaPluginDescriptor desc) {
    setPluginEnabled(desc, false);
    for (IdeaPluginDescriptor plugin : myAllPlugins) {
      if (ArrayUtil.contains(desc.getPluginId(), plugin.getDependentPluginIds()) &&
          !ArrayUtil.contains(desc.getPluginId(), plugin.getOptionalDependentPluginIds())) {
        setPluginDisabledWithDependents(plugin);
      }
    }
  }

  public boolean isForceEnable(IdeaPluginDescriptor descriptor) {
    return getDependentsOnEarlierPages(descriptor, true).size() > 0;
  }

  public List<IdeaPluginDescriptor> getDependentsOnEarlierPages(IdeaPluginDescriptor descriptor, boolean includeSamePage) {
    List<IdeaPluginDescriptor> dependents = new ArrayList<>();
    int thisStep = getPluginStepIndex(descriptor);
    for (IdeaPluginDescriptor dependent : myBackwardDependencies.get(descriptor)) {
      if (!myDisabledPluginIds.contains(dependent.getPluginId().toString())) {
        int index = getPluginStepIndex(dependent);
        if (index < thisStep || (includeSamePage && index == thisStep && getDependentsOnEarlierPages(dependent, true).size() > 0)) {
          dependents.add(dependent);
        }
      }
    }
    return dependents;
  }

  private int getPluginStepIndex(IdeaPluginDescriptor descriptor) {
    return getStepIndex(myPluginToStepMap.get(descriptor.getPluginId()));
  }
}
