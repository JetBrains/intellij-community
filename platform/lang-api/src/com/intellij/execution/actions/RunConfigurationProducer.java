// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.PossiblyDumbAware;
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
 * If RunConfigurationProducer doesn't require indexes, please, implement the {@link com.intellij.openapi.project.DumbAware} interface.
 */
public abstract class RunConfigurationProducer<T extends RunConfiguration> implements PossiblyDumbAware {
  public static final ExtensionPointName<RunConfigurationProducer> EP_NAME = ExtensionPointName.create("com.intellij.runConfigurationProducer");
  private static final Logger LOG = Logger.getInstance(RunConfigurationProducer.class);

  public static @NotNull List<RunConfigurationProducer<?>> getProducers(@NotNull Project project) {
    RunConfigurationProducerService runConfigurationProducerService = RunConfigurationProducerService.getInstance(project);
    List<RunConfigurationProducer> allProducers = DumbService.getInstance(project).filterByDumbAwareness(EP_NAME.getExtensionList());
    List<RunConfigurationProducer<?>> result = new ArrayList<>(allProducers.size());
    for (RunConfigurationProducer producer : allProducers) {
      if (!runConfigurationProducerService.isIgnored(producer)) {
        result.add(producer);
      }
    }
    return result;
  }

  private final ConfigurationFactory myConfigurationFactory;

  /**
   * @deprecated Use {@link LazyRunConfigurationProducer}.
   */
  @Deprecated
  protected RunConfigurationProducer(@NotNull ConfigurationFactory configurationFactory) {
    myConfigurationFactory = configurationFactory;
  }

  /**
   * @deprecated Use {@link LazyRunConfigurationProducer}.
   */
  @Deprecated(forRemoval = true)
  protected RunConfigurationProducer(@NotNull Class<? extends ConfigurationType> type) {
    this(ConfigurationTypeUtil.findConfigurationType(type));
  }

  /**
   * Do NOT use directly.
   */
  public RunConfigurationProducer(@SuppressWarnings("unused") boolean internalUsageOnly) {
    myConfigurationFactory = null;
  }

  /**
   * @deprecated Use {@link LazyRunConfigurationProducer}.
   */
  @Deprecated
  protected RunConfigurationProducer(@NotNull ConfigurationType configurationType) {
    myConfigurationFactory = configurationType.getConfigurationFactories()[0];
  }

  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return myConfigurationFactory;
  }

  public final @NotNull ConfigurationType getConfigurationType() {
    return getConfigurationFactory().getType();
  }

  /**
   * Creates a run configuration from the context.
   *
   * @param context contains the information about a location in the source code.
   * @return a container with a prepared run configuration and the context element from which it was created, or null if the context is
   * not applicable to this run configuration producer.
   */
  public @Nullable ConfigurationFromContext createConfigurationFromContext(@NotNull ConfigurationContext context) {
    final RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    Ref<PsiElement> ref = new Ref<>(context.getPsiLocation());
    try {
      //noinspection unchecked
      if (!setupConfigurationFromContext((T)settings.getConfiguration(), context, ref)) {
       return null;
     }
    }
    catch (ClassCastException e) {
      LOG.error(getConfigurationFactory() + " produced wrong type", e);
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
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
   * @return true if the context is applicable to this run configuration producer, false if the context is not applicable and the
   * configuration should be discarded.
   */
  protected abstract boolean setupConfigurationFromContext(@NotNull T configuration,
                                                           @NotNull ConfigurationContext context,
                                                           @NotNull Ref<PsiElement> sourceElement);

  /**
   * Checks if the specified configuration was created from the specified context.
   *
   * @param configuration a configuration instance.
   * @param context       contains the information about a location in the source code.
   * @return true if this configuration was created from the specified context, false otherwise.
   */
  public abstract boolean isConfigurationFromContext(@NotNull T configuration, @NotNull ConfigurationContext context);

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
  public @Nullable ConfigurationFromContext findOrCreateConfigurationFromContext(@NotNull ConfigurationContext context) {
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
        RunManager.getInstance(context.getProject()).setUniqueNameIfNeeded(fromContext.getConfiguration());
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
  public @Nullable RunnerAndConfigurationSettings findExistingConfiguration(@NotNull ConfigurationContext context) {
    final RunManager runManager = RunManager.getInstance(context.getProject());
    final List<RunnerAndConfigurationSettings> configurations = getConfigurationSettingsList(runManager);
    for (RunnerAndConfigurationSettings configurationSettings : configurations) {
      ProgressManager.checkCanceled();
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
  protected @NotNull List<RunnerAndConfigurationSettings> getConfigurationSettingsList(@NotNull RunManager runManager) {
    return runManager.getConfigurationSettingsList(getConfigurationFactory().getType());
  }

  protected RunnerAndConfigurationSettings cloneTemplateConfiguration(final @NotNull ConfigurationContext context) {
    return cloneTemplateConfigurationStatic(context, getConfigurationFactory());
  }

  protected static @NotNull RunnerAndConfigurationSettings cloneTemplateConfigurationStatic(final @NotNull ConfigurationContext context,
                                                                                            final @NotNull ConfigurationFactory configurationFactory) {
    final RunConfiguration original = context.getOriginalConfiguration(configurationFactory.getType());
    if (original != null) {
      return RunManager.getInstance(context.getProject()).createConfiguration(original.clone(), configurationFactory);
    }
    return RunManager.getInstance(context.getProject()).createConfiguration("", configurationFactory);
  }

  public static @NotNull <T extends RunConfigurationProducer> T getInstance(@NotNull Class<? extends T> aClass) {
    for (RunConfigurationProducer producer : EP_NAME.getExtensionList()) {
      if (aClass.isInstance(producer)) {
        //noinspection unchecked
        return (T)producer;
      }
    }
    assert false : aClass;
    return null;
  }

  public @Nullable RunConfiguration createLightConfiguration(final @NotNull ConfigurationContext context) {
    @SuppressWarnings("unchecked")
    T configuration = (T)getConfigurationFactory().createTemplateConfiguration(context.getProject());
    final Ref<PsiElement> ref = new Ref<>(context.getPsiLocation());
    try {
      if (!setupConfigurationFromContext(configuration, context, ref)) {
        return null;
      }
    }
    catch (ClassCastException e) {
      LOG.error(getConfigurationFactory() + " produced wrong type", e);
      return null;
    }
    return configuration;
  }
}
