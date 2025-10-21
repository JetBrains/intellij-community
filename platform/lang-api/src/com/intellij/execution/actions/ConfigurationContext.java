// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Context for creating run configurations from a location in the source code.
 *
 * @see RunConfigurationProducer
 */
public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance(ConfigurationContext.class);
  public static final Key<ConfigurationContext> SHARED_CONTEXT = Key.create("SHARED_CONTEXT");

  private Location<PsiElement> myLocation;
  private final Editor myEditor;
  private RunnerAndConfigurationSettings myConfiguration;
  private boolean myInitialized;
  private boolean myMultipleSelection;
  private volatile Ref<RunnerAndConfigurationSettings> myExistingConfiguration;
  private final @Nullable Module myModule;
  private final RunConfiguration myRuntimeConfiguration;
  private final DataContext myDataContext;
  private final String myPlace;

  private volatile List<RuntimeConfigurationProducer> myPreferredProducers;
  private volatile List<ConfigurationFromContext> myConfigurationsFromContext;


  /**
   * @deprecated use {@link ConfigurationContext#getFromContext(DataContext dataContext, String place)}
   */
  @Deprecated
  public static @NotNull ConfigurationContext getFromContext(DataContext dataContext) {
    return getFromContext(dataContext, ActionPlaces.UNKNOWN);
  }

  public static @NotNull ConfigurationContext getFromEvent(AnActionEvent event) {
    return event.getUpdateSession().sharedData(SHARED_CONTEXT, () -> {
      ConfigurationContext context = getFromContext(event.getDataContext(), event.getPlace());
      context.findExisting(); // precache
      context.getConfigurationsFromContext(); // precache
      DataManager.getInstance().saveInDataContext(event.getDataContext(), SHARED_CONTEXT, context);
      return context;
    });
  }

  public static @NotNull ConfigurationContext getFromContext(DataContext dataContext, String place) {
    DataManager dataManager = DataManager.getInstance();

    ConfigurationContext sharedContext = dataManager.loadFromDataContext(dataContext, SHARED_CONTEXT);
    Location<?> sharedLocation = sharedContext == null ? null : sharedContext.getLocation();
    PsiElement sharedPsiElement = sharedLocation == null ? null : sharedLocation.getPsiElement();

    Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    Location<PsiElement> location = calcLocation(dataContext, module);
    PsiElement psiElement = location == null ? null : location.getPsiElement();

    if (sharedLocation == null || location == null || !Comparing.equal(sharedPsiElement, psiElement)) {
      boolean isMultipleSelection = isMultipleSelection(dataContext);
      sharedContext = new ConfigurationContext(dataContext, location, module, isMultipleSelection, place);
      dataManager.saveInDataContext(dataContext, SHARED_CONTEXT, sharedContext);
    }
    return sharedContext;
  }

  public static @NotNull ConfigurationContext createEmptyContextForLocation(@NotNull Location location) {
    return new ConfigurationContext(location);
  }

  private ConfigurationContext(@NotNull DataContext dataContext,
                               @Nullable Location<PsiElement> location,
                               @Nullable Module module,
                               boolean multipleSelection,
                               String place) {
    RunConfiguration configuration = RunConfiguration.DATA_KEY.getData(dataContext);
    if (configuration == null) {
      ExecutionEnvironment environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(dataContext);
      if (environment != null) {
        myConfiguration = environment.getRunnerAndConfigurationSettings();
        if (myConfiguration != null) {
          myExistingConfiguration = Ref.create(myConfiguration);
          configuration = myConfiguration.getConfiguration();
        }
      }
    }
    myEditor = CommonDataKeys.EDITOR.getData(dataContext);
    myRuntimeConfiguration = configuration;
    myDataContext = dataContext;
    myModule = module;
    myLocation = location;
    myMultipleSelection = multipleSelection;
    myPlace = place;
  }

  private static @Nullable Location<PsiElement> calcLocation(@NotNull DataContext dataContext, Module module) {
    Location<?> location = Location.DATA_KEY.getData(dataContext);
    if (location != null) {
      //noinspection unchecked
      return (Location<PsiElement>)location;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      return null;
    }
    return new PsiLocation<>(project, module, element);
  }

  private static boolean isMultipleSelection(@NotNull DataContext dataContext) {
    Location<?> location = Location.DATA_KEY.getData(dataContext);
    Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
    PsiElement[] elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    return location != null && locations != null && locations.length > 1 ||
           elements != null && elements.length > 1 ||
           files != null && files.length > 1;
  }

  public ConfigurationContext(@NotNull PsiElement element) {
    myModule = ModuleUtilCore.findModuleForPsiElement(element);
    myLocation = new PsiLocation<>(element.getProject(), myModule, element);
    myRuntimeConfiguration = null;
    myDataContext = getDefaultDataContext();
    myEditor = null;
    myPlace = null;
  }

  private ConfigurationContext(@NotNull Location<PsiElement> location) {
    myLocation = location;
    myModule = location.getModule();
    myEditor = null;
    myRuntimeConfiguration = null;
    myDataContext = getDefaultDataContext();
    myPlace = null;
  }

  private void defaultSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.PROJECT, myLocation == null ? null : myLocation.getProject());
    sink.set(PlatformCoreDataKeys.MODULE, myModule);
    sink.set(Location.DATA_KEY, myLocation);
    sink.set(CommonDataKeys.EDITOR, myEditor);
    sink.set(CommonDataKeys.PSI_ELEMENT, myLocation == null ? null : myLocation.getPsiElement());
  }

  public @NotNull DataContext getDefaultDataContext() {
    return IdeUiService.getInstance().createCustomizedDataContext(
      DataContext.EMPTY_CONTEXT, (EdtNoGetDataProvider)this::defaultSnapshot);
  }
  
  public boolean containsMultipleSelection() {
    return myMultipleSelection;
  }

  /**
   * Returns the configuration created from this context.
   *
   * @return the configuration, or null if none of the producers were able to create a configuration from this context.
   */
  public synchronized @Nullable RunnerAndConfigurationSettings getConfiguration() {
    if (myConfiguration == null && !myInitialized) {
      createConfiguration();
    }
    return myConfiguration;
  }

  private void createConfiguration() {
    LOG.assertTrue(myConfiguration == null);
    final Location location = getLocation();
    myConfiguration = location != null ?
        PreferredProducerFind.createConfiguration(location, this) :
        null;
    myInitialized = true;
  }

  public synchronized void setConfiguration(@NotNull RunnerAndConfigurationSettings configuration) {
    myConfiguration = configuration;
    myInitialized = true;
  }

  /**
   * Returns the source code location for this context.
   *
   * @return the source code location, or null if no source code fragment is currently selected.
   */
  public @Nullable Location<PsiElement> getLocation() {
    if (myLocation == null || !myLocation.getPsiElement().isValid()) {
      if (myLocation != null) {
        myConfigurationsFromContext = null;
        myExistingConfiguration = null;
      }
      myLocation = calcLocation(myDataContext, myModule);
    }
    return myLocation;
  }

  /**
   * Returns the place for action which created this context.
   * @return the place for action which created this context.
   */
  public @Nullable String getPlace() { return myPlace; }

  /**
   * Returns the PSI element at caret for this context.
   *
   * @return the PSI element, or null if no source code fragment is currently selected.
   */
  public @Nullable PsiElement getPsiLocation() {
    return myLocation != null ? myLocation.getPsiElement() : null;
  }

  /**
   * Finds an existing run configuration matching the context.
   *
   * @return an existing configuration, or null if none was found.
   */
  public @Nullable RunnerAndConfigurationSettings findExisting() {
    Ref<RunnerAndConfigurationSettings> existingRef = myExistingConfiguration;
    if (existingRef != null) {
      RunnerAndConfigurationSettings configuration = existingRef.get();
      if (configuration == null || !Registry.is("suggest.all.run.configurations.from.context") || configuration.equals(myConfiguration)) {
        return configuration;
      }
    }
    Location<? extends PsiElement> location = getLocation();
    if (location == null) {
      myExistingConfiguration = Ref.create(null);
      return null;
    }

    PsiElement psiElement = location.getPsiElement();
    if (!psiElement.isValid()) {
      myExistingConfiguration = Ref.create(null);
      return null;
    }

    if (MultipleRunLocationsProvider.findAlternativeLocations(location) != null) {
      myExistingConfiguration = Ref.create(null);
      return null;
    }

    final List<RuntimeConfigurationProducer> producers = findPreferredProducers();
    List<ExistingConfiguration> existingConfigurations = new ArrayList<>();
    if (producers != null) {
      for (RuntimeConfigurationProducer producer : producers) {
        RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(location, this);
        if (configuration != null) {
          existingConfigurations.add(new ExistingConfiguration(configuration, null));
        }
      }
    }
    for (RunConfigurationProducer<?> producer : RunConfigurationProducer.getProducers(getProject())) {
      RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(this);
      if (configuration != null) {
        existingConfigurations.add(new ExistingConfiguration(configuration, producer));
      }
    }
    RunnerAndConfigurationSettings preferred = findPreferredConfiguration(existingConfigurations, psiElement);
    myExistingConfiguration = Ref.create(preferred);
    return preferred;
  }

  private @Nullable RunnerAndConfigurationSettings findPreferredConfiguration(@NotNull List<ExistingConfiguration> existingConfigurations,
                                                                              @NotNull PsiElement psiElement) {
    List<ConfigurationFromContext> configurationsFromContext = getConfigurationsFromContext();
    if (configurationsFromContext == null) return null;
    for (ExistingConfiguration configuration : existingConfigurations) {
      RunnerAndConfigurationSettings settings = configuration.getSettings();
      if (settings.equals(myConfiguration)) {
        return settings;
      }
      if (myRuntimeConfiguration != null && settings.getConfiguration() == myRuntimeConfiguration) {
        return settings;
      }
    }
    Set<RunnerAndConfigurationSettings> fromContextSettings =
      configurationsFromContext.stream().map(c -> c.getConfigurationSettings()).collect(Collectors.toSet());

    if (!ContainerUtil.exists(existingConfigurations, e -> fromContextSettings.contains(e.getSettings()))) {
      return null;
    }

    if (Registry.is("suggest.all.run.configurations.from.context")) {
      return null;
    }
    List<ConfigurationFromContext> contexts = ContainerUtil.mapNotNull(existingConfigurations, configuration -> {
      if (configuration.getProducer() == null || !fromContextSettings.contains(configuration.getSettings())) {
        return null;
      }
      return new ConfigurationFromContextImpl(configuration.getProducer(), configuration.getSettings(), psiElement);
    });
    if (!contexts.isEmpty()) {
      ConfigurationFromContext min = Collections.min(contexts, ConfigurationFromContext.COMPARATOR);
      return min.getConfigurationSettings();
    }
    ExistingConfiguration first = ContainerUtil.getFirstItem(existingConfigurations);
    return first != null ? first.getSettings() : null;
  }

  private static @Nullable PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
    PsiElement element = null;
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        final int offset = editor.getCaretModel().getOffset();
        element = psiFile.findElementAt(offset);
        if (element == null && offset > 0 && offset == psiFile.getTextLength()) {
          element = psiFile.findElementAt(offset-1);
        }
      }
    }
    if (element == null) {
      final PsiElement[] elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      element = elements != null && elements.length > 0 ? elements[0] : null;
    }
    if (element == null) {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null && files.length > 0) {
        element = PsiManager.getInstance(project).findFile(files[0]);
      }
    }
    return element;
  }

  public @NotNull RunManager getRunManager() {
    return RunManager.getInstance(getProject());
  }

  public Project getProject() {
    return myConfiguration == null ? myLocation.getProject() : myConfiguration.getConfiguration().getProject();
  }

  public @Nullable Module getModule() {
    return myModule;
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  /**
   * Returns original {@link RunConfiguration} from this context.
   * For example, it could be some test framework runtime configuration that had been launched
   * and that had brought a result test tree on which a right-click action was performed.
   *
   * @param type {@link ConfigurationType} instance to filter original runtime configuration by its type
   * @return {@link RunConfiguration} instance, it could be null
   */
  public @Nullable RunConfiguration getOriginalConfiguration(@Nullable ConfigurationType type) {
    if (type == null || (myRuntimeConfiguration != null && myRuntimeConfiguration.getType() == type)) {
      return myRuntimeConfiguration;
    }
    return null;
  }

  /**
   * Checks if the original run configuration matches the passed type.
   * If the original run configuration is undefined, the check is passed too.
   * An original run configuration is a run configuration associated with given context.
   * For example, it could be a test framework run configuration that had been launched
   * and that had brought a result test tree on which a right-click action was performed (and this context was created). In this case, other run configuration producers might want to not work on such elements.
   *
   * @param type {@link ConfigurationType} instance to match the original run configuration
   * @return true if the original run configuration is of the same type or it's undefined; false otherwise
   */
  public boolean isCompatibleWithOriginalRunConfiguration(@NotNull ConfigurationType type) {
    return myRuntimeConfiguration == null || myRuntimeConfiguration.getType() == type;
  }

  private @Nullable List<RuntimeConfigurationProducer> findPreferredProducers() {
    if (myPreferredProducers == null) {
      myPreferredProducers = PreferredProducerFind.findPreferredProducers(getLocation(), this, true);
    }
    return myPreferredProducers;
  }

  public @Nullable List<ConfigurationFromContext> getConfigurationsFromContext() {
    if (myConfigurationsFromContext == null) {
      myConfigurationsFromContext = PreferredProducerFind.getConfigurationsFromContext(getLocation(), this, true, true);
    }
    return myConfigurationsFromContext;
  }

  /**
   * The same as {@link #getConfigurationsFromContext()} but this method doesn't search among existing run configurations
   */
  public @Unmodifiable @Nullable List<ConfigurationFromContext> createConfigurationsFromContext() {
    // At the moment of writing, caching is not needed here, the result is cached outside.
    return PreferredProducerFind.getConfigurationsFromContext(getLocation(), this, true, false);
  }

  private static final class ExistingConfiguration {
    private final RunnerAndConfigurationSettings myConfigurationSettings;
    private final RunConfigurationProducer<?> myProducer;

    private ExistingConfiguration(@NotNull RunnerAndConfigurationSettings configurationSettings,
                                  @Nullable RunConfigurationProducer<?> producer) {
      myConfigurationSettings = configurationSettings;
      myProducer = producer;
    }

    private @NotNull RunnerAndConfigurationSettings getSettings() {
      return myConfigurationSettings;
    }

    private @Nullable RunConfigurationProducer<?> getProducer() {
      return myProducer;
    }
  }
}