/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Supports creating run configurations from context (by right-clicking a code element in the source editor or the project view). Typically,
 * run configurations that can be created from context should extend the {@link com.intellij.execution.configurations.LocatableConfigurationBase} class.
 *
 * @since 13
 * @author yole
 */
public abstract class RunConfigurationProducer<T extends RunConfiguration> {
  public static final ExtensionPointName<RunConfigurationProducer> EP_NAME = ExtensionPointName.create("com.intellij.runConfigurationProducer");
  private static final Logger LOG = Logger.getInstance(RunConfigurationProducer.class);

  @NotNull
  public static List<RunConfigurationProducer<?>> getProducers(@NotNull Project project) {
    RunConfigurationProducerService runConfigurationProducerService = RunConfigurationProducerService.getInstance(project);
    RunConfigurationProducer[] allProducers = Extensions.getExtensions(EP_NAME);
    List<RunConfigurationProducer<?>> result = new ArrayList<>(allProducers.length);
    for (RunConfigurationProducer producer : allProducers) {
      if (!runConfigurationProducerService.isIgnored(producer)) {
        result.add(producer);
      }
    }

    return result;
  }

  private final ConfigurationFactory myConfigurationFactory;

  protected RunConfigurationProducer(final ConfigurationFactory configurationFactory) {
    myConfigurationFactory = configurationFactory;
  }

  protected RunConfigurationProducer(final ConfigurationType configurationType) {
    myConfigurationFactory = configurationType.getConfigurationFactories()[0];
  }

  public ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  public ConfigurationType getConfigurationType() {
    return myConfigurationFactory.getType();
  }

  /**
   * Creates a run configuration from the context.
   *
   * @param context contains the information about a location in the source code.
   * @return a container with a prepared run configuration and the context element from which it was created, or null if the context is
   * not applicable to this run configuration producer.
   */
  @Nullable
  public ConfigurationFromContext createConfigurationFromContext(ConfigurationContext context) {
    final RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    Ref<PsiElement> ref = new Ref<>(context.getPsiLocation());
    try {
      //noinspection unchecked
      if (!setupConfigurationFromContext((T)settings.getConfiguration(), context, ref)) {
       return null;
     }
    }
    catch (ClassCastException e) {
      LOG.error(myConfigurationFactory + " produced wrong type", e);
      return null;
    }
    return new ConfigurationFromContextImpl(this, settings, ref.get());
  }

  /**
   * Sets up a configuration based on the specified context.
   *
   * @param configuration a clone of the template run configuration of the specified type
   * @param context       contains the information about a location in the source code.
   * @param sourceElement a reference to the source element for the run configuration (by default contains the element at caret,
   *                      can be updated by the producer to point to a higher-level element in the tree).
   *
   * @return true if the context is applicable to this run configuration producer, false if the context is not applicable and the
   * configuration should be discarded.
   */
  protected abstract boolean setupConfigurationFromContext(T configuration, ConfigurationContext context, Ref<PsiElement> sourceElement);

  /**
   * Checks if the specified configuration was created from the specified context.
   * @param configuration a configuration instance.
   * @param context       contains the information about a location in the source code.
   * @return true if this configuration was created from the specified context, false otherwise.
   */
  public abstract boolean isConfigurationFromContext(T configuration, ConfigurationContext context);

  /**
   * When two configurations are created from the same context by two different producers, checks if the configuration created by
   * this producer should be discarded in favor of the other one.
   *
   * @param self  a configuration created by this producer.
   * @param other a configuration created by another producer.
   * @return true if the configuration created by this producer is at least as good as the other one; false if this configuration
   * should be discarded and the other one should be used instead.
   * @see #shouldReplace(ConfigurationFromContext, ConfigurationFromContext)
   */
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return true;
  }

  /**
   * When two configurations are created from the same context by two different producers, checks if the configuration created by
   * this producer should replace the other one, that is if the other one should be discarded.
   *
   * <p>This is the same relationship as {@link #isPreferredConfiguration(ConfigurationFromContext, ConfigurationFromContext)} but
   * specified from the "replacement" side.
   *
   * @param self  a configuration created by this producer.
   * @param other a configuration created by another producer.
   * @return true if the other configuration should be discarded, false otherwise.
   * @see #isPreferredConfiguration(ConfigurationFromContext, ConfigurationFromContext)
   */
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return false;
  }

  /**
   * Called before a configuration created from context by this producer is first executed. Can be used to show additional UI for
   * customizing the created configuration.
   *
   * @param configuration a configuration created by this producer.
   * @param context       the context
   * @param startRunnable the runnable that needs to be called after additional customization is complete.
   */
  public void onFirstRun(@NotNull ConfigurationFromContext configuration, @NotNull ConfigurationContext context, @NotNull Runnable startRunnable) {
    startRunnable.run();
  }

  /**
   * Searches the list of existing run configurations to find one created from this context. Returns one if found, or tries to create
   * a new configuration from this context if not found.
   *
   * @param context contains the information about a location in the source code.
   * @return a configuration (new or existing) matching the context, or null if the context is not applicable to this producer.
   */
  @Nullable
  public ConfigurationFromContext findOrCreateConfigurationFromContext(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }

    ConfigurationFromContext fromContext = createConfigurationFromContext(context);
    if (fromContext == null) {
      return null;
    }

    final PsiElement psiElement = fromContext.getSourceElement();
    final Location<PsiElement> _location = PsiLocation.fromPsiElement(psiElement, location.getModule());
    if (_location != null) {
      // replace with existing configuration if any
      RunnerAndConfigurationSettings settings = findExistingConfiguration(context);
      if (settings == null) {
        RunManager.getInstance(context.getProject()).setUniqueNameIfNeed(fromContext.getConfiguration());
      }
      else {
        fromContext.setConfigurationSettings(settings);
      }
    }

    return fromContext;
  }

  /**
   * Searches the list of existing run configurations to find one created from this context. Returns one if found.
   *
   * @param context contains the information about a location in the source code.
   * @return an existing configuration matching the context, or null if no such configuration is found.
   */
  @Nullable
  public RunnerAndConfigurationSettings findExistingConfiguration(ConfigurationContext context) {
    final RunManager runManager = RunManager.getInstance(context.getProject());
    final List<RunnerAndConfigurationSettings> configurations = getConfigurationSettingsList(runManager);
    for (RunnerAndConfigurationSettings configurationSettings : configurations) {
      //noinspection unchecked
      if (isConfigurationFromContext((T) configurationSettings.getConfiguration(), context)) {
        return configurationSettings;
      }
    }
    return null;
  }

  /**
   * @return list of configurations that may match this producer
   */
  @NotNull
  protected List<RunnerAndConfigurationSettings> getConfigurationSettingsList(@NotNull RunManager runManager) {
    return runManager.getConfigurationSettingsList(myConfigurationFactory.getType());
  }

  protected RunnerAndConfigurationSettings cloneTemplateConfiguration(@NotNull final ConfigurationContext context) {
    return cloneTemplateConfigurationStatic(context, myConfigurationFactory);
  }

  @NotNull
  protected static RunnerAndConfigurationSettings cloneTemplateConfigurationStatic(@NotNull final ConfigurationContext context,
                                                                                   @NotNull final ConfigurationFactory configurationFactory) {
    final RunConfiguration original = context.getOriginalConfiguration(configurationFactory.getType());
    if (original != null) {
      return RunManager.getInstance(context.getProject()).createConfiguration(original.clone(), configurationFactory);
    }
    return RunManager.getInstance(context.getProject()).createRunConfiguration("", configurationFactory);
  }

  @NotNull
  public static <T extends RunConfigurationProducer> T getInstance(Class<? extends T> aClass) {
    for (RunConfigurationProducer producer : Extensions.getExtensions(EP_NAME)) {
      if (aClass.isInstance(producer)) {
        //noinspection unchecked
        return (T)producer;
      }
    }
    assert false : aClass;
    return null;
  }

  @Nullable
  public RunConfiguration createLightConfiguration(@NotNull final ConfigurationContext context) {
    @SuppressWarnings("unchecked")
    T configuration = (T)myConfigurationFactory.createTemplateConfiguration(context.getProject());
    final Ref<PsiElement> ref = new Ref<>(context.getPsiLocation());
    try {
      if (!setupConfigurationFromContext(configuration, context, ref)) {
        return null;
      }
    }
    catch (ClassCastException e) {
      LOG.error(myConfigurationFactory + " produced wrong type", e);
      return null;
    }
    return configuration;
  }
}
