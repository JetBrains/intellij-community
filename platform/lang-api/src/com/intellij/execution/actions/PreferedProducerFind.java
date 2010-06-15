/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class PreferedProducerFind {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.PreferedProducerFind");

  private PreferedProducerFind() {}

  @Nullable
  public static RunnerAndConfigurationSettings createConfiguration(@NotNull Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer preferredProducer = findPreferredProducer(location, context);
    return preferredProducer != null ? preferredProducer.getConfiguration() : null;
  }

  @Nullable
  public static List<RuntimeConfigurationProducer> findPreferredProducers(final Location location, final ConfigurationContext context, final boolean strict) {
    if (location == null) {
      return null;
    }
    
    final RuntimeConfigurationProducer[] configurationProducers =
      ApplicationManager.getApplication().getExtensions(RuntimeConfigurationProducer.RUNTIME_CONFIGURATION_PRODUCER);
    final ArrayList<RuntimeConfigurationProducer> producers = new ArrayList<RuntimeConfigurationProducer>();
    for (final RuntimeConfigurationProducer prototype : configurationProducers) {
      final RuntimeConfigurationProducer producer = prototype.createProducer(location, context);
      if (producer.getConfiguration() != null) {
        LOG.assertTrue(producer.getSourceElement() != null, producer);
        producers.add(producer);
      }
    }
    if (producers.isEmpty()) { //try to find by locatable type
      final ConfigurationType[] factories = RunManager.getInstance(location.getProject()).getConfigurationFactories();
      for (final ConfigurationType type : factories) {
        if (type instanceof LocatableConfigurationType) {
          final DefaultRuntimeConfigurationProducer prototype = new DefaultRuntimeConfigurationProducer(((LocatableConfigurationType)type));
          final RuntimeConfigurationProducer producer = prototype.createProducer(location, context);
          if (producer.getConfiguration() != null) {
            producers.add(producer);
          }
        }
      }
    }
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

  @Nullable
  private static RuntimeConfigurationProducer findPreferredProducer(final Location location, final ConfigurationContext context) {
    final List<RuntimeConfigurationProducer> producers = findPreferredProducers(location, context, true);
    if (producers != null){
      return producers.get(0);
    }
    return null;
  }

  private static class DefaultRuntimeConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
    private PsiElement myPsiElement;

    public DefaultRuntimeConfigurationProducer(final LocatableConfigurationType configurationType) {
      super(configurationType);
    }

    public PsiElement getSourceElement() {
      return myPsiElement;
    }

    @Nullable
    protected RunnerAndConfigurationSettings createConfigurationByElement(final Location location, final ConfigurationContext context) {
      myPsiElement = location.getPsiElement();
      return ((LocatableConfigurationType)getConfigurationType()).createConfigurationByLocation(location);
    }

    @Override
    protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                   @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                   ConfigurationContext context) {
      if (existingConfigurations.length > 0) {
        ConfigurationType type = existingConfigurations[0].getType();
        if (type instanceof LocatableConfigurationType) {
          for (final RunnerAndConfigurationSettings configuration : existingConfigurations) {
            if (((LocatableConfigurationType)type).isConfigurationByLocation(configuration.getConfiguration(), location)) {
              return configuration;
            }
          }
        }
      }
      return null;
    }

    public int compareTo(final Object o) {
      return PREFERED;
    }
  }
}
