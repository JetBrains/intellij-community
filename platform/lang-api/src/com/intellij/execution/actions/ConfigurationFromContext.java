// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Describes a run configuration being created by a context run action.
 *
 * @see RunConfigurationProducer
 */
public abstract class ConfigurationFromContext {

  private boolean myIsFromAlternativeLocation;

  private @Nullable @Nls String myAlternativeLocationDisplayName;

  /**
   * Returns the created run configuration settings.
   *
   * @return the created run configuration settings.
   */
  public abstract @NotNull RunnerAndConfigurationSettings getConfigurationSettings();

  public abstract void setConfigurationSettings(RunnerAndConfigurationSettings configurationSettings);

  /**
   * Returns the run configuration object for the created configuration.
   *
   * @return the run configuration object.
   */
  public @NotNull RunConfiguration getConfiguration() {
    return getConfigurationSettings().getConfiguration();
  }

  /**
   * Returns the type of the created configuration.
   *
   * @return the configuration type.
   */
  public @NotNull ConfigurationType getConfigurationType() {
    return getConfiguration().getType();
  }

  /**
   * Returns the element from which this configuration was created. Configurations created from a lower-level element (for example, a
   * method) take precedence over configurations created from a higher-level element (for example, a class).
   *
   * @return the PSI element from which the configuration was created.
   */
  public abstract @NotNull PsiElement getSourceElement();

  /**
   * Called before the configuration created from context is first executed. Can be used to show additional UI for customizing the
   * created configuration.
   *
   * @param context       the context
   * @param startRunnable the runnable that needs to be called after additional customization is complete.
   */
  public void onFirstRun(ConfigurationContext context, Runnable startRunnable) {
    startRunnable.run();
  }

  /**
   * Checks if this configuration should be discarded in favor of another configuration created from the same context.
   *
   * @param other another configuration created from the same context.
   * @return true if this configuration is at least as good as the other one; false if this configuration should be discarded and the
   * other one should be used instead.
   */
  public boolean isPreferredTo(ConfigurationFromContext other) {
    return true;
  }

  /**
   * Checks if this configuration should replace another one, that is if the other should be discarded.
   *
   * @see RunConfigurationProducer#shouldReplace(ConfigurationFromContext, ConfigurationFromContext)
   * @return true if the other configuration should be discarded, false otherwise.
   */
  public boolean shouldReplace(ConfigurationFromContext other) {
    return false;
  }

  /**
   * Checks if this configuration was created by the specified producer.
   *
   * @param producerClass the run configuration producer class.
   * @return true if the configuration was created by this producer, false otherwise.
   */
  public boolean isProducedBy(Class<? extends RunConfigurationProducer> producerClass) {
    return false;
  }

  @Override
  public String toString() {
    return getConfigurationSettings().toString();
  }

  /**
   * Return if this configuration was created from alternative location provided by {@link MultipleRunLocationsProvider}.
   *
   * @return true if the configuration was created from alternative location, false otherwise.
   */
  public boolean isFromAlternativeLocation() {
    return myIsFromAlternativeLocation;
  }

  public void setFromAlternativeLocation(boolean isFromAlternativeLocation) {
    this.myIsFromAlternativeLocation = isFromAlternativeLocation;
  }

  /**
   * Return alternative location display name provided by {@link MultipleRunLocationsProvider}.
   *
   * @return Location display name, null if name was not provided or this configuration is not from alternative location.
   */
  public @Nullable @Nls String getAlternativeLocationDisplayName() {
    return myAlternativeLocationDisplayName;
  }

  public void setAlternativeLocationDisplayName(@Nullable @Nls String alternativeLocationDisplayName) {
    this.myAlternativeLocationDisplayName = alternativeLocationDisplayName;
  }


  /**
   * Compares configurations according to precedence.
   */
  public static final Comparator<ConfigurationFromContext> COMPARATOR = (configuration1, configuration2) -> {
    if (PsiTreeUtil.isAncestor(configuration1.getSourceElement(), configuration2.getSourceElement(), true)) {
      return 1;
    }
    if (PsiTreeUtil.isAncestor(configuration2.getSourceElement(), configuration1.getSourceElement(), true)) {
      return -1;
    }
    // If neither configuration1 nor configuration2 are preferred to each other, then these are considered equal.
    if (!configuration1.isPreferredTo(configuration2) && !configuration2.isPreferredTo(configuration1)) {
      return 0;
    }
    if (!configuration1.isPreferredTo(configuration2)) {
      return 1;
    }
    if (configuration2.shouldReplace(configuration1)) {
      return 1;
    }
    if (!configuration2.isPreferredTo(configuration1)) {
      return -1;
    }
    if (configuration1.shouldReplace(configuration2)) {
      return -1;
    }
    return 0;
  };

  /**
   * Compares configurations according to configuration type name.
   */
  public static final Comparator<ConfigurationFromContext> NAME_COMPARATOR = Comparator.comparing(p -> p.getConfigurationType().getDisplayName());
}
