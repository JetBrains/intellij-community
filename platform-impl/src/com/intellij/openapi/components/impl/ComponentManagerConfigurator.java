package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManagerConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;

import java.net.URL;
import java.util.Map;

class ComponentManagerConfigurator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.ComponentManagerConfigurator");

  private static final Map<String, ComponentManagerConfig> ourDescriptorToConfig = new HashMap<String, ComponentManagerConfig>();

  private final ComponentManagerImpl myComponentManager;

  public ComponentManagerConfigurator(final ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
  }

  private void loadConfiguration(final ComponentConfig[] configs, final boolean defaultProject, final IdeaPluginDescriptor descriptor) {
    for (ComponentConfig config : configs) {
      loadSingleConfig(defaultProject, config, descriptor);
    }
  }

  private void loadSingleConfig(final boolean defaultProject, final ComponentConfig config, final IdeaPluginDescriptor descriptor) {
    if (defaultProject && config.skipForDefaultProject) return;
    if (!myComponentManager.isComponentSuitable(config.options)) return;

    myComponentManager.registerComponent(config, descriptor);
  }

  private void loadComponentsConfiguration(String descriptor, String layer, boolean defaultProject) {
    try {
      ComponentManagerConfig managerConfig = ourDescriptorToConfig.get(descriptor);

      if (managerConfig == null) {
        final URL url = DecodeDefaultsUtil.getDefaults(this, descriptor);

        assert url != null : "Defaults not found:" + descriptor;

        managerConfig = XmlSerializer.deserialize(url, ComponentManagerConfig.class);

        ourDescriptorToConfig.put(descriptor, managerConfig);
      }

      assert managerConfig != null;
      ComponentConfig[] componentConfigs;

      if (layer.equals(ComponentManagerConfig.APPLICATION_COMPONENTS)) {
        componentConfigs = managerConfig.applicationComponents;
      }
      else if (layer.equals(ComponentManagerConfig.PROJECT_COMPONENTS)) {
        componentConfigs = managerConfig.projectComponents;
      }
      else if (layer.equals(ComponentManagerConfig.MODULE_COMPONENTS)) {
        componentConfigs = managerConfig.moduleComponents;
      }
      else {
        throw new IllegalArgumentException("Unsupported layer: "+ layer);
      }

      loadComponentsConfiguration(componentConfigs, null, defaultProject);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void loadComponentsConfiguration(String layer, final boolean defaultProject) {
    loadComponentsConfiguration(ApplicationManagerEx.getApplicationEx().getComponentsDescriptor(), layer, defaultProject);
  }

  public void loadComponentsConfiguration(final ComponentConfig[] components,
                                          final IdeaPluginDescriptor descriptor,
                                          final boolean defaultProject) {
    if (components == null) return;

    loadConfiguration(components, defaultProject, descriptor);
  }
}
