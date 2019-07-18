// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.idea.Main;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class EAPUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "user.advanced.info";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return collectMetrics();
  }

  @NotNull
  private static Set<MetricEvent> collectMetrics() {
    try {
      if (!Main.isHeadless()) {
        final Set<MetricEvent> result = new HashSet<>();
        if (ApplicationInfoEx.getInstanceEx().isEAP()) {
          result.add(MetricEventFactoryKt.newMetric("eap"));
          result.add(newBuildMetric("eap"));
        }
        else {
          result.add(MetricEventFactoryKt.newMetric("release"));
          result.add(newBuildMetric("release"));
        }
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade != null) {
          // non-eap commercial version
          if (facade.isEvaluationLicense()) {
            result.add(MetricEventFactoryKt.newMetric("evaluation"));
            result.add(newLicencingMetric("evaluation"));
          }
          else if (!StringUtil.isEmpty(facade.getLicensedToMessage())){
            result.add(MetricEventFactoryKt.newMetric("license"));
            result.add(newLicencingMetric("license"));
          }
        }
        return result;
      }
    }
    catch (Throwable e) {
      //ignore
    }
    return Collections.emptySet();
  }

  @NotNull
  private static MetricEvent newLicencingMetric(@NotNull String value) {
    return MetricEventFactoryKt.newMetric("licencing", value);
  }

  @NotNull
  private static MetricEvent newBuildMetric(@NotNull String value) {
    return MetricEventFactoryKt.newMetric("build", value);
  }
}
