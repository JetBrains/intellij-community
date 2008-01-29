package com.intellij.execution.actions;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
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
  public static RunnerAndConfigurationSettingsImpl createConfiguration(@NotNull Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer preferedProducer = findPreferedProducer(location, context);
    return preferedProducer != null ? preferedProducer.getConfiguration() : null;
  }

  @Nullable
  public static List<RuntimeConfigurationProducer> findPreferedProducers(final Location location, final ConfigurationContext context) {
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
    final RuntimeConfigurationProducer first = producers.get(0);
    for (Iterator<RuntimeConfigurationProducer> it = producers.iterator(); it.hasNext();) {
      RuntimeConfigurationProducer producer = it.next();
      if (RuntimeConfigurationProducer.COMPARATOR.compare(producer, first) >= 0) {
        it.remove();
      }
    }
    return producers;
  }

  @Nullable
  private static RuntimeConfigurationProducer findPreferedProducer(final Location location, final ConfigurationContext context) {
    final List<RuntimeConfigurationProducer> producers = findPreferedProducers(location, context);
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
    protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
      myPsiElement = location.getPsiElement();
      return (RunnerAndConfigurationSettingsImpl)((LocatableConfigurationType)getConfigurationType()).createConfigurationByLocation(location);
    }

    public int compareTo(final Object o) {
      return PREFERED;
    }
  }
}
