// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.actions;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
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

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Context for creating run configurations from a location in the source code.
 *
 * @see RunConfigurationProducer
 */
public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance(ConfigurationContext.class);
  private final Location<PsiElement> myLocation;
  private RunnerAndConfigurationSettings myConfiguration;
  private boolean myInitialized;
  private boolean myMultipleSelection;
  private Ref<RunnerAndConfigurationSettings> myExistingConfiguration;
  private final Module myModule;
  private final RunConfiguration myRuntimeConfiguration;
  private final Component myContextComponent;

  public static Key<ConfigurationContext> SHARED_CONTEXT = Key.create("SHARED_CONTEXT");
  private List<RuntimeConfigurationProducer> myPreferredProducers;
  private List<ConfigurationFromContext> myConfigurationsFromContext;

  @NotNull
  public static ConfigurationContext getFromContext(DataContext dataContext) {
    DataManager dataManager = DataManager.getInstance();
    ConfigurationContext sharedContext = dataManager.loadFromDataContext(dataContext, SHARED_CONTEXT);
    Pair<Location<PsiElement>, Boolean> calculatedLocation = null;
    Module module = null;
    if (sharedContext == null ||
        sharedContext.getLocation() == null ||
        (calculatedLocation = calcLocation(dataContext, module = LangDataKeys.MODULE.getData(dataContext))).getFirst() == null ||
        !Comparing.equal(sharedContext.getLocation().getPsiElement(), calculatedLocation.getFirst().getPsiElement())) {
      if (calculatedLocation==null) {
        module = LangDataKeys.MODULE.getData(dataContext);
        calculatedLocation = calcLocation(dataContext, module);
      }
      sharedContext = new ConfigurationContext(dataContext, calculatedLocation.getFirst(), module, calculatedLocation.getSecond());
      dataManager.saveInDataContext(dataContext, SHARED_CONTEXT, sharedContext);
    }
    return sharedContext;
  }

  @NotNull
  public static ConfigurationContext createEmptyContextForLocation(@NotNull Location location) {
    return new ConfigurationContext(location);
  }

  private ConfigurationContext(final DataContext dataContext, Location<PsiElement> location, Module module, boolean multipleSelection) {
    myRuntimeConfiguration = RunConfiguration.DATA_KEY.getData(dataContext);
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    myModule = module;
    myLocation = location;
    myMultipleSelection = multipleSelection;
  }

  @NotNull
  private static Pair<Location<PsiElement>,Boolean> calcLocation(@NotNull DataContext dataContext, Module module) {
    @SuppressWarnings({"unchecked"}) Location<PsiElement> location = (Location<PsiElement>)Location.DATA_KEY.getData(dataContext);
    if (location != null) {
      Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
      boolean myMultipleSelection = locations != null && locations.length > 1;
      return Pair.create(location, myMultipleSelection);
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return Pair.create(null, false);
    }
    final PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      return Pair.create(null, false);
    }
    location = new PsiLocation<>(project, module, element);
    final PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    boolean myMultipleSelection;
    if (elements != null) {
      myMultipleSelection = elements.length > 1;
    }
    else {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      myMultipleSelection = files != null && files.length > 1;
    }
    return Pair.create(location, myMultipleSelection);
  }

  public ConfigurationContext(PsiElement element) {
    myModule = ModuleUtilCore.findModuleForPsiElement(element);
    myLocation = new PsiLocation<>(element.getProject(), myModule, element);
    myRuntimeConfiguration = null;
    myContextComponent = null;
  }

  private ConfigurationContext(@NotNull Location location) {
    //noinspection unchecked
    myLocation = location;
    myModule = location.getModule();
    myRuntimeConfiguration = null;
    myContextComponent = null;
  }

  public boolean containsMultipleSelection() {
    return myMultipleSelection;
  }

  /**
   * Returns the configuration created from this context.
   *
   * @return the configuration, or null if none of the producers were able to create a configuration from this context.
   */
  @Nullable
  public synchronized RunnerAndConfigurationSettings getConfiguration() {
    if (myConfiguration == null && !myInitialized) {
      createConfiguration();
    }
    return myConfiguration;
  }

  private void createConfiguration() {
    LOG.assertTrue(myConfiguration == null);
    final Location location = getLocation();
    myConfiguration = location != null && !DumbService.isDumb(location.getProject()) ?
        PreferredProducerFind.createConfiguration(location, this) :
        null;
    myInitialized = true;
  }

  public synchronized void setConfiguration(@NotNull RunnerAndConfigurationSettings configuration) {
    myConfiguration = configuration;
    myInitialized = true;
  }

  @Deprecated
  @Nullable
  public RunnerAndConfigurationSettings updateConfiguration(final RuntimeConfigurationProducer producer) {
    myConfiguration = producer.getConfiguration();
    return myConfiguration;
  }

  /**
   * Returns the source code location for this context.
   *
   * @return the source code location, or null if no source code fragment is currently selected.
   */
  @Nullable
  public Location getLocation() {
    return myLocation;
  }

  /**
   * Returns the PSI element at caret for this context.
   *
   * @return the PSI element, or null if no source code fragment is currently selected.
   */
  @Nullable
  public PsiElement getPsiLocation() {
    return myLocation != null ? myLocation.getPsiElement() : null;
  }

  /**
   * Finds an existing run configuration matching the context.
   *
   * @return an existing configuration, or null if none was found.
   */
  @Nullable
  public RunnerAndConfigurationSettings findExisting() {
    if (myExistingConfiguration != null) {
      RunnerAndConfigurationSettings configuration = myExistingConfiguration.get();
      if (configuration == null || !Registry.is("suggest.all.run.configurations.from.context") || configuration.equals(myConfiguration)) {
        return configuration;
      }
    }
    myExistingConfiguration = new Ref<>();
    if (myLocation == null) {
      return null;
    }

    final PsiElement psiElement = myLocation.getPsiElement();
    if (!psiElement.isValid()) {
      return null;
    }

    if (MultipleRunLocationsProvider.findAlternativeLocations(myLocation) != null) {
      myExistingConfiguration.set(null);
      return null;
    }

    final List<RuntimeConfigurationProducer> producers = findPreferredProducers();
    List<ExistingConfiguration> existingConfigurations = new ArrayList<>();
    if (producers != null) {
      for (RuntimeConfigurationProducer producer : producers) {
        RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(myLocation, this);
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
    myExistingConfiguration.set(findPreferredConfiguration(existingConfigurations, psiElement));
    return myExistingConfiguration.get();
  }

  @Nullable
  private RunnerAndConfigurationSettings findPreferredConfiguration(@NotNull List<ExistingConfiguration> existingConfigurations,
                                                                    @NotNull PsiElement psiElement) {
    for (ExistingConfiguration configuration : existingConfigurations) {
      RunnerAndConfigurationSettings settings = configuration.getSettings();
      if (settings.equals(myConfiguration)) {
        return settings;
      }
      if (myRuntimeConfiguration != null && settings.getConfiguration() == myRuntimeConfiguration) {
        return settings;
      }
    }
    if (Registry.is("suggest.all.run.configurations.from.context")) {
      return null;
    }
    List<ConfigurationFromContext> contexts = ContainerUtil.mapNotNull(existingConfigurations, configuration -> {
      if (configuration.getProducer() == null) {
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

  @Nullable
  private static PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
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
      final PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
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

  @NotNull
  public RunManager getRunManager() {
    return RunManager.getInstance(getProject());
  }

  public Project getProject() {
    return myLocation.getProject();
  }

  public Module getModule() {
    return myModule;
  }

  public DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(myContextComponent);
  }

  /**
   * Returns original {@link RunConfiguration} from this context.
   * For example, it could be some test framework runtime configuration that had been launched
   * and that had brought a result test tree on which a right-click action was performed.
   *
   * @param type {@link ConfigurationType} instance to filter original runtime configuration by its type
   * @return {@link RunConfiguration} instance, it could be null
   */
  @Nullable
  public RunConfiguration getOriginalConfiguration(@Nullable ConfigurationType type) {
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

  @Deprecated
  @Nullable
  public List<RuntimeConfigurationProducer> findPreferredProducers() {
    if (myPreferredProducers == null) {
      myPreferredProducers = PreferredProducerFind.findPreferredProducers(myLocation, this, true);
    }
    return myPreferredProducers;
  }

  @Nullable
  public List<ConfigurationFromContext> getConfigurationsFromContext() {
    if (myConfigurationsFromContext == null) {
      myConfigurationsFromContext = PreferredProducerFind.getConfigurationsFromContext(myLocation, this, true);
    }
    return myConfigurationsFromContext;
  }

  private static final class ExistingConfiguration {
    private final RunnerAndConfigurationSettings myConfigurationSettings;
    private final RunConfigurationProducer<?> myProducer;

    private ExistingConfiguration(@NotNull RunnerAndConfigurationSettings configurationSettings,
                                  @Nullable RunConfigurationProducer<?> producer) {
      myConfigurationSettings = configurationSettings;
      myProducer = producer;
    }

    @NotNull
    private RunnerAndConfigurationSettings getSettings() {
      return myConfigurationSettings;
    }

    @Nullable
    private RunConfigurationProducer<?> getProducer() {
      return myProducer;
    }
  }
}
