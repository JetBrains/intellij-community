/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.execution.actions;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.ConfigurationFromContextWrapper;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class PreferredProducerFind {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.PreferredProducerFind");

  private PreferredProducerFind() {}

  @Nullable
  public static RunnerAndConfigurationSettings createConfiguration(@NotNull Location location, final ConfigurationContext context) {
    final ConfigurationFromContext fromContext = findConfigurationFromContext(location, context);
    return fromContext != null ? fromContext.getConfigurationSettings() : null;
  }

  @Nullable
  @Deprecated
  public static List<RuntimeConfigurationProducer> findPreferredProducers(final Location location, final ConfigurationContext context, final boolean strict) {
    if (location == null) {
      return null;
    }
    final List<RuntimeConfigurationProducer> producers = findAllProducers(location, context);
    if (producers.isEmpty()) return null;
    Collections.sort(producers, RuntimeConfigurationProducer.COMPARATOR);

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
    //todo load configuration types if not already loaded
    Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP);
    final RuntimeConfigurationProducer[] configurationProducers =
      ApplicationManager.getApplication().getExtensions(RuntimeConfigurationProducer.RUNTIME_CONFIGURATION_PRODUCER);
    final ArrayList<RuntimeConfigurationProducer> producers = new ArrayList<>();
    for (final RuntimeConfigurationProducer prototype : configurationProducers) {
      final RuntimeConfigurationProducer producer;
      try {
        producer = prototype.createProducer(location, context);
      }
      catch (AbstractMethodError e) {
        LOG.error(new ExtensionException(prototype.getClass()));
        continue;
      }
      if (producer.getConfiguration() != null) {
        LOG.assertTrue(producer.getSourceElement() != null, producer);
        producers.add(producer);
      }
    }
    return producers;
  }

  @Nullable
  public static List<ConfigurationFromContext> getConfigurationsFromContext(final Location location,
                                                                            final ConfigurationContext context,
                                                                            final boolean strict) {
    if (location == null) {
      return null;
    }

    final ArrayList<ConfigurationFromContext> configurationsFromContext = new ArrayList<>();
    for (RuntimeConfigurationProducer producer : findAllProducers(location, context)) {
      configurationsFromContext.add(new ConfigurationFromContextWrapper(producer));
    }

    for (RunConfigurationProducer producer : RunConfigurationProducer.getProducers(context.getProject())) {
      ConfigurationFromContext fromContext = producer.findOrCreateConfigurationFromContext(context);
      if (fromContext != null) {
        configurationsFromContext.add(fromContext);
      }
    }

    if (configurationsFromContext.isEmpty()) return null;
    Collections.sort(configurationsFromContext, ConfigurationFromContext.COMPARATOR);

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
  private static ConfigurationFromContext findConfigurationFromContext(final Location location, final ConfigurationContext context) {
    final List<ConfigurationFromContext> producers = getConfigurationsFromContext(location, context, true);
    if (producers != null){
      return producers.get(0);
    }
    return null;
  }
}
