// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.diagnostic.PluginException;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ConfigurationFromContextWrapper;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class PreferredProducerFind {
  private static final Logger LOG = Logger.getInstance(PreferredProducerFind.class);

  private PreferredProducerFind() {}

  @Nullable
  public static RunnerAndConfigurationSettings createConfiguration(@NotNull Location location, @NotNull ConfigurationContext context) {
    List<ConfigurationFromContext> configsFromContext = getConfigurationsFromContext(location, context, true, true);
    ConfigurationFromContext fromContext = !ContainerUtil.isEmpty(configsFromContext) ? configsFromContext.get(0) : null;
    return fromContext != null ? fromContext.getConfigurationSettings() : null;
  }

  @Nullable
  @Deprecated(forRemoval = true)
  public static List<RuntimeConfigurationProducer> findPreferredProducers(final Location location, final ConfigurationContext context, final boolean strict) {
    if (location == null) {
      return null;
    }
    final List<RuntimeConfigurationProducer> producers = findAllProducers(location, context);
    if (producers.isEmpty()) return null;
    producers.sort(RuntimeConfigurationProducer.COMPARATOR);

    if(strict) {
      final RuntimeConfigurationProducer first = producers.get(0);
      for (Iterator<RuntimeConfigurationProducer> it = producers.iterator(); it.hasNext();) {
        RuntimeConfigurationProducer producer = it.next();
        if (producer != first && RuntimeConfigurationProducer.COMPARATOR.compare(producer, first) >= 0) {
          it.remove();
        }
      }
    }

    return producers;
  }

  private static List<RuntimeConfigurationProducer> findAllProducers(Location location, ConfigurationContext context) {
    final ArrayList<RuntimeConfigurationProducer> producers = new ArrayList<>();
    for (final RuntimeConfigurationProducer prototype : RuntimeConfigurationProducer.RUNTIME_CONFIGURATION_PRODUCER.getExtensionList()) {
      final RuntimeConfigurationProducer producer;
      try {
        producer = prototype.createProducer(location, context);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(PluginException.createByClass(e, prototype.getClass()));
        continue;
      }

      if (producer.getConfiguration() != null) {
        LOG.assertTrue(producer.getSourceElement() != null, producer);
        producers.add(producer);
      }
    }
    return producers;
  }

  /**
   * @param strict         <code>true</code>means that this method should return only the best (one or several equally good) run configurations,
   *                       according to {@link ConfigurationFromContext#COMPARATOR}
   * @param preferExisting if <code>true</code> then {@link RunConfigurationProducer#findOrCreateConfigurationFromContext(ConfigurationContext)} will be used;
   *                       if <code>false</code> then {@link RunConfigurationProducer#createConfigurationFromContext(ConfigurationContext)} will be used.
   */
  public static @Nullable List<ConfigurationFromContext> getConfigurationsFromContext(Location location,
                                                                                      @NotNull ConfigurationContext context,
                                                                                      boolean strict,
                                                                                      boolean preferExisting) {
    if (location == null) {
      return null;
    }

    MultipleRunLocationsProvider.AlternativeLocationsInfo
      alternativeLocations = MultipleRunLocationsProvider.findAlternativeLocations(location);
    if (alternativeLocations == null) {
      return doGetConfigurationsFromContext(location, context, strict, preferExisting);
    }

    return getConfigurationsFromAlternativeLocations(alternativeLocations, location, strict, preferExisting);
  }

  private static @Nullable List<ConfigurationFromContext> doGetConfigurationsFromContext(@NotNull Location location,
                                                                                         @NotNull ConfigurationContext context,
                                                                                         boolean strict,
                                                                                         boolean preferExisting) {
    final ArrayList<ConfigurationFromContext> configurationsFromContext = new ArrayList<>();
    for (RuntimeConfigurationProducer producer : findAllProducers(location, context)) {
      configurationsFromContext.add(new ConfigurationFromContextWrapper(producer));
    }

    for (RunConfigurationProducer producer : RunConfigurationProducer.getProducers(context.getProject())) {
      ProgressManager.checkCanceled();
      ConfigurationFromContext fromContext = preferExisting
                                             ? producer.findOrCreateConfigurationFromContext(context)
                                             : producer.createConfigurationFromContext(context);
      if (fromContext != null) {
        configurationsFromContext.add(fromContext);
      }
    }

    if (configurationsFromContext.isEmpty()) return null;
    configurationsFromContext.sort(ConfigurationFromContext.COMPARATOR);

    if(strict) {
      final ConfigurationFromContext first = configurationsFromContext.get(0);
      for (Iterator<ConfigurationFromContext> it = configurationsFromContext.iterator(); it.hasNext();) {
        ConfigurationFromContext producer = it.next();
        if (producer != first && ConfigurationFromContext.COMPARATOR.compare(producer, first) > 0) {
          it.remove();
        }
      }
    }

    return configurationsFromContext;
  }

  @Nullable
  private static List<ConfigurationFromContext> getConfigurationsFromAlternativeLocations(
    @NotNull MultipleRunLocationsProvider.AlternativeLocationsInfo alternativeLocationsInfo,
    @NotNull Location originalLocation,
    boolean strict,
    boolean preferExisting
  ) {
    List<ConfigurationFromContext> result = new SmartList<>();
    for (Location alternativeLocation : alternativeLocationsInfo.getAlternativeLocations()) {
      ConfigurationContext fakeContextForAlternativeLocation = ConfigurationContext.createEmptyContextForLocation(alternativeLocation);
      List<ConfigurationFromContext> configurationsForLocation =
        doGetConfigurationsFromContext(alternativeLocation, fakeContextForAlternativeLocation, strict, preferExisting);
      if (configurationsForLocation != null) {
        for (ConfigurationFromContext configurationFromContext : configurationsForLocation) {
          configurationFromContext.setFromAlternativeLocation(true);
          String locationDisplayName = alternativeLocationsInfo.getProvider().getLocationDisplayName(alternativeLocation, originalLocation);
          configurationFromContext.setAlternativeLocationDisplayName(locationDisplayName);
          RunConfiguration configuration = configurationFromContext.getConfiguration();
          if (configuration instanceof LocatableConfigurationBase && ((LocatableConfiguration)configuration).isGeneratedName()) {
            configuration.setName(configuration.getName() + " " + locationDisplayName);
            ((LocatableConfigurationBase<?>)configuration).setNameChangedByUser(true);
          }
        }

        result.addAll(configurationsForLocation);
      }
    }
    return ContainerUtil.nullize(result);
  }
}
