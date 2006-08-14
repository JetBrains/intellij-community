package com.intellij.execution.impl;

import com.intellij.execution.RunManagerConfig;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.util.RefactoringElementListenerComposite;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.util.Function;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class RunManagerImpl extends RunManagerEx implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.RunManager");

  private final Project myProject;

  private Map<String, ConfigurationType> myTypesByName = new LinkedHashMap<String, ConfigurationType>();

  private Map<String, RunnerAndConfigurationSettingsImpl> myConfigurations = new LinkedHashMap<String, RunnerAndConfigurationSettingsImpl>(); // template configurations are not included here
  private Map<String, Boolean> mySharedConfigurations = new TreeMap<String, Boolean>();
  private Map<String, Map<String, Boolean>> myMethod2CompileBeforeRun = new TreeMap<String, Map<String, Boolean>>();

  private Map<String, Function<RunConfiguration, String>> myRegisteredSteps = new LinkedHashMap<String, Function<RunConfiguration, String>>();
  private Map<String, Function<RunConfiguration, String>> myRegisteredDescriptions = new LinkedHashMap<String, Function<RunConfiguration, String>>();

  private Map<ConfigurationFactory, RunnerAndConfigurationSettingsImpl> myTemplateConfigurationsMap = new HashMap<ConfigurationFactory, RunnerAndConfigurationSettingsImpl>();
  private RunnerAndConfigurationSettingsImpl mySelectedConfiguration = null;
  private String mySelectedConfig = null;
  protected MyRefactoringElementListenerProvider myRefactoringElementListenerProvider;
  private RunnerAndConfigurationSettingsImpl myTempConfiguration;

  @NonNls
  private static final String TEMP_CONFIGURATION = "tempConfiguration";
  @NonNls
  private static final String CONFIGURATION = "configuration";
  private ConfigurationType[] myTypes;
  private final RunManagerConfig myConfig;
  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String SELECTED_ATTR = "selected";
  @NonNls private static final String METHOD = "method";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String VALUE = "value";

  public RunManagerImpl(final Project project,
                        PropertiesComponent propertiesComponent,
                        ConfigurationType[] configurationTypes) {
    myConfig = new RunManagerConfig(propertiesComponent, this);
    myProject = project;
    myRefactoringElementListenerProvider = new MyRefactoringElementListenerProvider();
    initializeConfigurationTypes(configurationTypes);

    for (final RunnerAndConfigurationSettingsImpl settings : myConfigurations.values()) {
      RunConfiguration configuration = settings.getConfiguration();
      if (configuration instanceof ModuleBasedConfiguration) {
        ((ModuleBasedConfiguration)configuration).init();
      }
    }
    registerStepBeforeRun(RunManagerConfig.MAKE, null, null);
  }

  // separate method needed for tests
  public final void initializeConfigurationTypes(final ConfigurationType[] factories) {
    LOG.assertTrue(factories != null);
    Arrays.sort(factories, new Comparator<ConfigurationType>() {
      public int compare(final ConfigurationType o1, final ConfigurationType o2) {
        return o1.getDisplayName().compareTo(o2.getDisplayName());
      }
    });
    myTypes = factories;
    for (final ConfigurationType type : factories) {
      myTypesByName.put(type.getComponentName(), type);
    }
  }

  public void disposeComponent() { }

  public void initComponent() {
  }

  public void projectOpened() {
    installRefactoringListener();
  }

  // separate method needed for tests
  public void installRefactoringListener() {
    RefactoringListenerManager.getInstance(myProject).addListenerProvider(myRefactoringElementListenerProvider);
  }

  // separate method needed for tests
  public void uninstallRefactoringListener() {
    RefactoringListenerManager.getInstance(myProject).removeListenerProvider(myRefactoringElementListenerProvider);
  }

  public RunnerAndConfigurationSettingsImpl createConfiguration(final String name, final ConfigurationFactory factory) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(factory != null);
    RunnerAndConfigurationSettingsImpl template = getConfigurationTemplate(factory);
    RunConfiguration configuration = factory.createConfiguration(name, template.getConfiguration());
    setCompileMethodBeforeRun(configuration, getCompileMethodBeforeRun(template.getConfiguration()));
    shareConfiguration(configuration, isConfigurationShared(template));
    createStepsBeforeRun(template, configuration);
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this, configuration, false);
    settings.importRunnerAndConfigurationSettings(template);
    return settings;
  }

  public void createStepsBeforeRun(final RunnerAndConfigurationSettingsImpl template, final RunConfiguration configuration) {
    AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
    if (antConfiguration != null) {
      final RunConfiguration templateConfiguration = template.getConfiguration();
      final AntBuildTarget antBuildTarget = antConfiguration.getTargetForBeforeRunEvent(templateConfiguration.getType(), templateConfiguration.getName());
      if (antBuildTarget != null){
        antConfiguration.setTargetForBeforeRunEvent(antBuildTarget.getModel().getBuildFile(), antBuildTarget.getName(), templateConfiguration.getType(), configuration.getName());
      }
    }
  }

  public void projectClosed() {
    uninstallRefactoringListener();
  }


  public RunManagerConfig getConfig() {
    return myConfig;
  }

  public ConfigurationType[] getConfigurationFactories() {
    return myTypes.clone();
  }

  /**
   * Template configuration is not included
   */
  public RunConfiguration[] getConfigurations(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    final List<RunConfiguration> array = new ArrayList<RunConfiguration>();
    for (RunnerAndConfigurationSettingsImpl myConfiguration : myConfigurations.values()) {
      final RunConfiguration configuration = myConfiguration.getConfiguration();
      if (type.equals(configuration.getType())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunConfiguration[array.size()]);
  }

  public RunConfiguration[] getAllConfigurations() {
    RunConfiguration [] result = new RunConfiguration[myConfigurations.size()];
    int i = 0;
    for (Iterator<RunnerAndConfigurationSettingsImpl> iterator = myConfigurations.values().iterator(); iterator.hasNext();i++) {
      RunnerAndConfigurationSettings settings = iterator.next();
      result[i] = settings.getConfiguration();
    }

    return result;
  }

  public RunnerAndConfigurationSettings getSettings(RunConfiguration configuration){
    for (RunnerAndConfigurationSettingsImpl settings : myConfigurations.values()) {
      if (settings.getConfiguration() == configuration) return settings;
    }
    return null;
  }

  /**
   * Template configuration is not included
   */
  public RunnerAndConfigurationSettingsImpl[] getConfigurationSettings(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    final LinkedHashSet<RunnerAndConfigurationSettings> array = new LinkedHashSet<RunnerAndConfigurationSettings>();
    for (RunnerAndConfigurationSettingsImpl configuration : myConfigurations.values()) {
      if (type.equals(configuration.getType())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunnerAndConfigurationSettingsImpl[array.size()]);
  }

  public RunnerAndConfigurationSettingsImpl getConfigurationTemplate(final ConfigurationFactory factory) {
    RunnerAndConfigurationSettingsImpl template = myTemplateConfigurationsMap.get(factory);
    if (template == null) {
      template = new RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(myProject), true);
      myTemplateConfigurationsMap.put(factory, template);
    }
    return template;
  }

  public void addConfiguration(RunnerAndConfigurationSettingsImpl settings, boolean shared, Map<String,Boolean> method) {
    final String configName = getUniqueName(settings.getConfiguration());
    if (!myConfigurations.containsKey(configName)){ //do not add shared configuration twice
      myConfigurations.put(configName, settings);
    }
    mySharedConfigurations.put(configName, shared);
    myMethod2CompileBeforeRun.put(configName, method);
  }

  private static String getUniqueName(final RunConfiguration settings) {
    return settings.getType().getDisplayName() + "." + settings.getName();
  }

  public void removeConfigurations(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    //for (Iterator<Pair<RunConfiguration, JavaProgramRunner>> it = myRunnerPerConfigurationSettings.keySet().iterator(); it.hasNext();) {
    //  final Pair<RunConfiguration, JavaProgramRunner> pair = it.next();
    //  if (type.equals(pair.getFirst().getType())) {
    //    it.remove();
    //  }
    //}
    for (Iterator<RunnerAndConfigurationSettingsImpl> it = myConfigurations.values().iterator(); it.hasNext();) {
      final RunnerAndConfigurationSettings configuration = it.next();
      if (type.equals(configuration.getType())) {
        it.remove();
      }
    }

    if (myTempConfiguration != null && type.equals(myTempConfiguration.getType())) {
      myTempConfiguration = null;
    }
  }


  public RunnerAndConfigurationSettingsImpl getSelectedConfiguration() {
    if (mySelectedConfiguration == null && mySelectedConfig != null){
      mySelectedConfiguration = myConfigurations.get(mySelectedConfig);
      mySelectedConfig = null;
    }
    return mySelectedConfiguration;
  }

  public void setSelectedConfiguration(final RunnerAndConfigurationSettingsImpl configuration) {
    mySelectedConfiguration = configuration;
  }

  public static boolean canRunConfiguration(final RunConfiguration configuration) {
    LOG.assertTrue(configuration != null);
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationError er) {
      return false;
    }
    catch (RuntimeConfigurationException e) {
      return true;
    }
    return true;
  }

  public void writeExternal(final Element parentNode) throws WriteExternalException {
    LOG.assertTrue(parentNode != null);

    if (myTempConfiguration != null) {
      addConfigurationElement(parentNode, myTempConfiguration, TEMP_CONFIGURATION);
    }

    for (final RunnerAndConfigurationSettingsImpl runnerAndConfigurationSettings : myTemplateConfigurationsMap.values()) {
      addConfigurationElement(parentNode, runnerAndConfigurationSettings);
    }

    final Collection<RunnerAndConfigurationSettingsImpl> configurations = getStableConfigurations().values();
    for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
      addConfigurationElement(parentNode, configuration); //write shared configurations twice to save order     
    }
    if (mySelectedConfiguration != null){
      parentNode.setAttribute(SELECTED_ATTR, getUniqueName(mySelectedConfiguration.getConfiguration()));
    }
  }

  public void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettingsImpl template) throws WriteExternalException {
    addConfigurationElement(parentNode, template, CONFIGURATION);
  }

  private void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettingsImpl template, String elementType) throws WriteExternalException {
    final Element configurationElement = new Element(elementType);
    parentNode.addContent(configurationElement);
    template.writeExternal(configurationElement);
    final Map<String, Boolean> methods = myMethod2CompileBeforeRun.get(getUniqueName(template.getConfiguration()));
    if (methods != null) {
      Element methodsElement = new Element(METHOD);
      for (String key : methods.keySet()) {
        final Element child = new Element(OPTION);
        child.setAttribute(NAME_ATTR, key);
        child.setAttribute(VALUE, String.valueOf(methods.get(key)));
        methodsElement.addContent(child);
      }
      configurationElement.addContent(methodsElement);
    }
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    myConfigurations.clear();

    final List children = parentNode.getChildren();
    for (final Object aChildren : children) {
      final Element element = (Element)aChildren;
      loadConfiguration(element, false);
    }
    mySelectedConfig = parentNode.getAttributeValue(SELECTED_ATTR);
  }

  public void loadConfiguration(final Element element, boolean isShared) throws InvalidDataException {
    RunnerAndConfigurationSettingsImpl configuration = new RunnerAndConfigurationSettingsImpl(this);
    configuration.readExternal(element);
    ConfigurationFactory factory = configuration.getFactory();
    if (factory == null) return;

    final Element methodsElement = element.getChild(METHOD);
    if (configuration.isTemplate()) {
      myTemplateConfigurationsMap.put(factory, configuration);
      final Map<String, Boolean> map = updateStepsBeforeRun(methodsElement);
      setCompileMethodBeforeRun(configuration.getConfiguration(), map);
    }
    else {
      if (Boolean.valueOf(element.getAttributeValue(SELECTED_ATTR)).booleanValue()) { //to support old style
        mySelectedConfiguration = configuration;
      }
      if (TEMP_CONFIGURATION.equals(element.getName())) {
        myTempConfiguration = configuration;
      }
      final Map<String, Boolean> map = updateStepsBeforeRun(methodsElement);
      addConfiguration(configuration, isShared, map);
    }
  }

  private static Map<String, Boolean> updateStepsBeforeRun(final Element child) {
    if (child == null){
      return null;
    }
    final List list = child.getChildren(OPTION);
    final Map<String, Boolean> map = new HashMap<String, Boolean>();
    for (Object o : list) {
      Element methodElement = (Element)o;
      final String methodName = methodElement.getAttributeValue(NAME_ATTR);
      final Boolean enabled = Boolean.valueOf(methodElement.getAttributeValue(VALUE));
      map.put(methodName, enabled);
    }
    return map;
  }


  public ConfigurationFactory getFactory(final String typeName, String factoryName) {
    final ConfigurationType type = myTypesByName.get(typeName);
    if (factoryName == null) {
      factoryName = type != null ? type.getConfigurationFactories()[0].getName() : null;
    }
    return findFactoryOfTypeNameByName(typeName, factoryName);
  }


  private ConfigurationFactory findFactoryOfTypeNameByName(final String typeName, final String factoryName) {
    return findFactoryOfTypeByName(myTypesByName.get(typeName), factoryName);
  }

  private static ConfigurationFactory findFactoryOfTypeByName(final ConfigurationType type, final String factoryName) {
    if (type == null || factoryName == null) return null;
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

  public void setTemporaryConfiguration(final RunnerAndConfigurationSettingsImpl tempConfiguration) {
    LOG.assertTrue(tempConfiguration != null);
    myConfigurations = getStableConfigurations();
    myTempConfiguration = tempConfiguration;
    addConfiguration(myTempConfiguration, isConfigurationShared(tempConfiguration), getCompileMethodBeforeRun(tempConfiguration.getConfiguration()));
    setActiveConfiguration(myTempConfiguration);
  }

  public void setActiveConfiguration(final RunnerAndConfigurationSettingsImpl configuration) {
    setSelectedConfiguration(configuration);
  }

  public Map<String, RunnerAndConfigurationSettingsImpl> getStableConfigurations() {
    final Map<String,RunnerAndConfigurationSettingsImpl> result = new LinkedHashMap<String, RunnerAndConfigurationSettingsImpl>(myConfigurations);
    if (myTempConfiguration != null) {
      result.remove(getUniqueName(myTempConfiguration.getConfiguration()));
    }
    return result;
  }

  public boolean isTemporary(final RunConfiguration configuration) {
    return myTempConfiguration != null && myTempConfiguration.getConfiguration() == configuration;
  }

  public boolean isTemporary(RunnerAndConfigurationSettingsImpl settings) {
    return settings.equals(myTempConfiguration);
  }

  public RunConfiguration getTempConfiguration() {
    return myTempConfiguration == null ? null : myTempConfiguration.getConfiguration();
  }

  public void makeStable(final RunConfiguration configuration) {
    if (isTemporary(configuration)) {
      myTempConfiguration = null;
    }
  }

  public RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type) {
    return createConfiguration(name, type);
  }

  public void registerStepBeforeRun(String actionName, Function<RunConfiguration, String> action, Function<RunConfiguration, String> retrieveDescription) {
    myRegisteredSteps.put(actionName, action);
    myRegisteredDescriptions.put(actionName, retrieveDescription);
  }

  public Set<String> getRegisteredStepsBeforeRun() {
    return myRegisteredSteps.keySet();
  }

  public Function<RunConfiguration, String> getStepBeforeRun(String actionName) {
    return myRegisteredSteps.get(actionName);
  }

  public String getStepBeforeRunDescription(String actionName, RunConfiguration runConfiguration) {
    return myRegisteredDescriptions.get(actionName).fun(runConfiguration);
  }


  public boolean isConfigurationShared(final RunnerAndConfigurationSettingsImpl settings){
    Boolean shared = mySharedConfigurations.get(getUniqueName(settings.getConfiguration()));
    if (shared == null) {
      final RunnerAndConfigurationSettingsImpl template = getConfigurationTemplate(settings.getFactory());
      shared = mySharedConfigurations.get(getUniqueName(template.getConfiguration()));
    }
    return shared != null && shared.booleanValue();
  }

  public Map<String, Boolean> getCompileMethodBeforeRun(final RunConfiguration settings){
    Map<String, Boolean> method = myMethod2CompileBeforeRun.get(getUniqueName(settings));
    if (method == null) {
      final RunnerAndConfigurationSettingsImpl template = getConfigurationTemplate(settings.getFactory());
      method = myMethod2CompileBeforeRun.get(getUniqueName(template.getConfiguration()));
    }
    if (method == null){
      method = new HashMap<String, Boolean>();
      method.put(RunManagerConfig.MAKE, Boolean.TRUE);
    }
    return method;
  }

  public void shareConfiguration(final RunConfiguration runConfiguration, final boolean shareConfiguration) {
    mySharedConfigurations.put(getUniqueName(runConfiguration), shareConfiguration);
  }

  public void setCompileMethodBeforeRun(final RunConfiguration runConfiguration, Map<String,Boolean> method) {
    myMethod2CompileBeforeRun.put(getUniqueName(runConfiguration), method);
  }

  public void addConfiguration(final RunnerAndConfigurationSettingsImpl settings, final boolean isShared) {
    final HashMap<String, Boolean> map = new HashMap<String, Boolean>();
    map.put(RunManagerConfig.MAKE, Boolean.TRUE);
    addConfiguration(settings, isShared, map);
  }

  private class MyRefactoringElementListenerProvider implements RefactoringElementListenerProvider {
    public RefactoringElementListener getListener(final PsiElement element) {
      RefactoringElementListenerComposite composite = null;
      for (RunnerAndConfigurationSettingsImpl settings : myConfigurations.values()) {
        final RunConfiguration configuration = settings.getConfiguration();
        if (configuration instanceof RuntimeConfiguration) { // todo: perhaps better way to handle listeners?
          final RefactoringElementListener listener = ((RuntimeConfiguration)configuration).getRefactoringElementListener(element);
          if (listener != null) {
            if (composite == null) {
              composite = new RefactoringElementListenerComposite();
            }
            composite.addListener(listener);
          }
        }
      }
      return composite;
    }
  }

  public static RunManagerImpl getInstanceImpl(final Project project) {
    return (RunManagerImpl)getInstance(project);
  }
}
