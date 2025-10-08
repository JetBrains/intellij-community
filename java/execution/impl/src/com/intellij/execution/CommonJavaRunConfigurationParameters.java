// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.execution.configurations.ModuleBasedConfigurationOptions;
import com.intellij.execution.impl.statistics.FusAwareRunConfiguration;
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public interface CommonJavaRunConfigurationParameters extends CommonProgramRunConfigurationParameters, ConfigurationWithAlternativeJre,
                                                              FusAwareRunConfiguration {
  void setVMParameters(@Nullable String value);

  String getVMParameters();

  @Override
  boolean isAlternativeJrePathEnabled();

  void setAlternativeJrePathEnabled(boolean enabled);

  @Override
  @Nullable
  String getAlternativeJrePath();

  void setAlternativeJrePath(@Nullable String path);

  /**
   * Returns the binary name of the class to run (see JLS 13.1).
   * <p>
   * This binary name is <i>usually</i> the same as the fully qualified name of the main class
   * (returned from {@link SingleClassConfiguration#getMainClass()}, which is usually used by implementations of this method).
   * <p>
   * For nested classes, the fully qualified class name differs from the binary class name. See JLS 13.1.
   * To know if the main class returned from {@link SingleClassConfiguration#getMainClass()} is nested or not, we need to resolve it,
   * and because of that, implementations of this method <i>usually</i> require read lock.
   * @see com.intellij.psi.util.ClassUtil#getBinaryClassName
   */
  @Nullable
  String getRunClass();

  @Nullable
  String getPackage();

  default List<ModuleBasedConfigurationOptions.ClasspathModification> getClasspathModifications() {
    return Collections.emptyList();
  }

  default void setClasspathModifications(List<ModuleBasedConfigurationOptions.ClasspathModification> modifications) {
  }

  @Override
  default @Unmodifiable @NotNull List<EventPair<?>> getAdditionalUsageData() {
    EventPair<Integer> data = getAlternativeJreUserData(getAlternativeJrePath());
    return ContainerUtil.createMaybeSingletonList(data);
  }

  static EventPair<Integer> getAlternativeJreUserData(String jrePath) {
    if (jrePath != null) {
      JavaVersion version = JavaParametersUtil.getJavaVersion(jrePath);
      if (version != null) {
        return RunConfigurationUsageTriggerCollector.ALTERNATIVE_JRE_VERSION.with(version.feature);
      }
    }
    return null;
  }
}
