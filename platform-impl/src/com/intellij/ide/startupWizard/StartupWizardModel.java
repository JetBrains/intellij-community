package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.wizard.WizardModel;
import com.intellij.util.containers.HashSet;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author yole
 */
public class StartupWizardModel extends WizardModel {
  private Set<String> myDisabledPluginIds = new HashSet<String>();
  private Map<String, SelectPluginsStep> myStepMap = new HashMap<String, SelectPluginsStep>();
  private SelectPluginsStep myOtherStep = new SelectPluginsStep("Select other plugins", myDisabledPluginIds, null);
  private File myOldConfigPath;
  private SelectPluginsStep myHtmlStep;

  public StartupWizardModel() {
    super(ApplicationNamesInfo.getInstance().getFullProductName() + " Startup Wizard");

    add(new ImportOldConfigsStep());
    addSelectPluginsStep("VCS Integration", "Select VCS Integration Plugins", null);
    addSelectPluginsStep("Web/JavaEE Technologies", "Select Web/JavaEE Technology Plugins", null);
    addSelectPluginsStep("Application Servers", "Select Application Server Plugins", "com.intellij.javaee");
    addSelectPluginsStep("HTML/JavaScript Development", "Select HTML/JavaScript Development Plugins", null);
    add(myOtherStep);

    final IdeaPluginDescriptor[] pluginDescriptors = PluginManager.loadDescriptors();
    for (IdeaPluginDescriptor pluginDescriptor : pluginDescriptors) {
      if (pluginDescriptor.getPluginId().getIdString().equals("com.intellij")) {
        // skip 'IDEA CORE' plugin
        continue;
      }
      SelectPluginsStep step = myStepMap.get(pluginDescriptor.getCategory());
      if (step != null) {
        step.addPlugin(pluginDescriptor);
      }
      else {
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

  public File getOldConfigPath() {
    return myOldConfigPath;
  }

  public void setOldConfigPath(final File oldConfigPath) {
    myOldConfigPath = oldConfigPath;
  }
}
