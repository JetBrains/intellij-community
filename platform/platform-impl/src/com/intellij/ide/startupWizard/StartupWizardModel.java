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
package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.ui.wizard.WizardModel;
import com.intellij.util.containers.HashSet;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class StartupWizardModel extends WizardModel {
  private final Set<String> myDisabledPluginIds = new HashSet<String>();
  private final Map<String, SelectPluginsStep> myStepMap = new HashMap<String, SelectPluginsStep>();
  private SelectPluginsStep myOtherStep;

  public StartupWizardModel(final List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(ApplicationNamesInfo.getInstance().getFullProductName() + " Initial Configuration Wizard");
    loadDisabledPlugins(new File(PathManager.getConfigPath()));

    for (ApplicationInfoEx.PluginChooserPage page : pluginChooserPages) {
      if (page.getCategory() == null) {
        myOtherStep = new SelectPluginsStep(page.getTitle(), myDisabledPluginIds, null);
      }
      else {
        addSelectPluginsStep(page.getCategory(), page.getTitle(), page.getDependentPlugin());
      }
    }
    if (myOtherStep != null) {
      add(myOtherStep);
    }

    final IdeaPluginDescriptor[] pluginDescriptors = PluginManager.loadDescriptors();
    for (IdeaPluginDescriptor pluginDescriptor : pluginDescriptors) {
      if (pluginDescriptor.getPluginId().getIdString().equals("com.intellij")) {
        // skip 'IDEA CORE' plugin
        continue;
      }
      PluginManager.initClassLoader(getClass().getClassLoader(), (IdeaPluginDescriptorImpl) pluginDescriptor);
      SelectPluginsStep step = myStepMap.get(pluginDescriptor.getCategory());
      if (step != null) {
        step.addPlugin(pluginDescriptor);
      }
      else if (myOtherStep != null) {
        myOtherStep.addPlugin(pluginDescriptor);
      }
    }
    for (SelectPluginsStep step : myStepMap.values()) {
      step.fillPlugins();
    }
    myOtherStep.fillPlugins();
  }

  private SelectPluginsStep addSelectPluginsStep(final String category, final String title, final String requirePlugin) {
    final SelectPluginsStep step = new SelectPluginsStep(title, myDisabledPluginIds, requirePlugin);
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
}
