package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author dyoma
 */
public class RunnerAndConfigurationSettingsImpl implements JDOMExternalizable, Cloneable, RunnerAndConfigurationSettings, Comparable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunnerAndConfigurationSettings");

  @NonNls private static final String RUNNER_ELEMENT = "RunnerSettings";
  @NonNls private static final String CONFIGURATION_ELEMENT = "ConfigurationWrapper";
  @NonNls private static final String RUNNER_ID = "RunnerId";
  @NonNls private static final String CONFIGURATION_TYPE_ATTRIBUTE = "type";
  @NonNls private static final String FACTORY_NAME_ATTRIBUTE = "factoryName";
  @NonNls private static final String TEMPLATE_FLAG_ATTRIBUTE = "default";

  private final RunManagerImpl myManager;
  private RunConfiguration myConfiguration;
  private boolean myIsTemplate;

  private Map<JavaProgramRunner, RunnerSettings> myRunnerSettings = new TreeMap<JavaProgramRunner, RunnerSettings>(createComparator());
  private Map<JavaProgramRunner, ConfigurationPerRunnerSettings> myConfigurationPerRunnerSettings = new TreeMap<JavaProgramRunner, ConfigurationPerRunnerSettings>(createComparator());

  private static Comparator<? super JavaProgramRunner> createComparator() {
    return new Comparator<JavaProgramRunner>() {
      public int compare(final JavaProgramRunner runner1, final JavaProgramRunner runner2) {
        return runner1.getInfo().getId().compareTo(runner2.getInfo().getId());
      }
    };
  }

  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String DUMMY_ELEMENT_NANE = "dummy";

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager) {
    myManager = manager;
  }

  public RunnerAndConfigurationSettingsImpl(RunManagerImpl manager, RunConfiguration configuration, boolean isTemplate) {
    myManager = manager;
    myConfiguration = configuration;
    myIsTemplate = isTemplate;
  }

  public ConfigurationFactory getFactory() {
    return myConfiguration == null ? null : myConfiguration.getFactory();
  }

  public boolean isTemplate() {
    return myIsTemplate;
  }

  public RunConfiguration getConfiguration() {
    return myConfiguration;
  }

  public Factory<RunnerAndConfigurationSettingsImpl> createFactory() {
    return new Factory<RunnerAndConfigurationSettingsImpl>() {
      public RunnerAndConfigurationSettingsImpl create() {
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

  private ConfigurationFactory getFactory(final Element element) {
    final String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
    String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
    return myManager.getFactory(typeName, factoryName);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myIsTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE)).booleanValue();
    final ConfigurationFactory factory = getFactory(element);
    if (factory == null) return;

    if (myIsTemplate) {
      myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
    }
    else {
      final String name = element.getAttributeValue(NAME_ATTR);
      myConfiguration = myManager.createConfiguration(name, factory).getConfiguration();
    }

    myConfiguration.readExternal(element);
    List runners = element.getChildren(RUNNER_ELEMENT);
    for (final Object runner1 : runners) {
      Element runnerElement = (Element)runner1;
      String id = runnerElement.getAttributeValue(RUNNER_ID);
      JavaProgramRunner runner = ExecutionRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        RunnerSettings settings = createRunnerSettings(runner);
        settings.readExternal(runnerElement);
        myRunnerSettings.put(runner, settings);
      }
    }

    List configurations = element.getChildren(CONFIGURATION_ELEMENT);
    for (final Object configuration : configurations) {
      Element configurationElement = (Element)configuration;
      String id = configurationElement.getAttributeValue(RUNNER_ID);
      JavaProgramRunner runner = ExecutionRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        ConfigurationPerRunnerSettings settings =
          new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
        settings.readExternal(configurationElement);
        myConfigurationPerRunnerSettings.put(runner, settings);
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    final ConfigurationFactory factory = myConfiguration.getFactory();

    element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, String.valueOf(myIsTemplate));
    if (!myIsTemplate) {
      element.setAttribute(NAME_ATTR, myConfiguration.getName());
    }
    element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.getType().getComponentName());
    element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.getName());
    myConfiguration.writeExternal(element);

    for (JavaProgramRunner runner : myRunnerSettings.keySet()) {
      RunnerSettings settings = myRunnerSettings.get(runner);
      Element runnerElement = new Element(RUNNER_ELEMENT);
      settings.writeExternal(runnerElement);
      element.addContent(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getInfo().getId());
    }

    for (JavaProgramRunner runner : myConfigurationPerRunnerSettings.keySet()) {
      ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
      Element runnerElement = new Element(CONFIGURATION_ELEMENT);
      settings.writeExternal(runnerElement);
      element.addContent(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getInfo().getId());
    }
  }

  public RunnerSettings getRunnerSettings(JavaProgramRunner runner) {
    RunnerSettings settings = myRunnerSettings.get(runner);
    if (settings == null) {
      settings = createRunnerSettings(runner);
      myRunnerSettings.put(runner, settings);
    }
    return settings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings(JavaProgramRunner runner) {
    ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
    if (settings == null) {
      settings = new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
      myConfigurationPerRunnerSettings.put(runner, settings);
    }
    return settings;
  }

  public ConfigurationType getType() {
    return myConfiguration == null ? null : myConfiguration.getType();
  }

  public RunnerAndConfigurationSettingsImpl clone() {
    RunnerAndConfigurationSettingsImpl copy = new RunnerAndConfigurationSettingsImpl(myManager, myConfiguration.clone(), false);
    copy.importRunnerAndConfigurationSettings(this);
    return copy;
  }

  public void importRunnerAndConfigurationSettings(RunnerAndConfigurationSettingsImpl template) {
    try {
      for (JavaProgramRunner runner : template.myRunnerSettings.keySet()) {
        RunnerSettings data = createRunnerSettings(runner);
        myRunnerSettings.put(runner, data);
        Element temp = new Element(DUMMY_ELEMENT_NANE);
        template.myRunnerSettings.get(runner).writeExternal(temp);
        data.readExternal(temp);
      }

      for (JavaProgramRunner runner : template.myConfigurationPerRunnerSettings.keySet()) {
        ConfigurationPerRunnerSettings data =
          new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
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

  private RunnerSettings createRunnerSettings(final JavaProgramRunner runner) {
    return new RunnerSettings<JDOMExternalizable>(runner.createConfigurationData(new InfoProvider(runner)), myConfiguration);
  }

  public int compareTo(final Object o) {
    if (o instanceof RunnerAndConfigurationSettings) {
      return getName().compareTo(((RunnerAndConfigurationSettings)o).getName());
    }
    return 0;
  }

  private class InfoProvider implements ConfigurationInfoProvider {
    private JavaProgramRunner myRunner;

    public InfoProvider(JavaProgramRunner runner) {
      myRunner = runner;
    }

    public JavaProgramRunner getRunner() {
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
