// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics;

import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Additional field returned from {@link FusAwareRunConfiguration#getAdditionalUsageData()} method of
 * this interface implementation should be registered in two {@link FeatureUsageCollectorExtension} EP implementations:
 * <ul>
 *   <li>Group {@link RunConfigurationUsageTriggerCollector#GROUP}, event {@code "started"}</li>
 *   <li>Group {@link RunConfigurationTypeUsagesCollector#GROUP}, event {@link RunConfigurationTypeUsagesCollector#CONFIGURED_IN_PROJECT }</li>
 * <ul/>
 *
 * @see RunConfigurationUsageLanguageExtension
 * @see RunConfigurationTypeLanguageExtension
 */
public interface FusAwareRunConfiguration {
  @Unmodifiable
  @NotNull List<EventPair<?>> getAdditionalUsageData();
}
