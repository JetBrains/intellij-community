// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.idea.Main;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class EAPUsageCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("user.advanced.info", 4);
  private static final EventId EAP = GROUP.registerEvent("eap");
  private static final EventId RELEASE = GROUP.registerEvent("release");
  private static final EventId1<BuildType> BUILD = GROUP.registerEvent("build", EventFields.Enum("value", BuildType.class));
  private static final EventId EVALUATION = GROUP.registerEvent("evaluation");
  private static final EventId LICENSE = GROUP.registerEvent("license");
  private static final EnumEventField<LicenceType> LICENSE_VALUE = EventFields.Enum("value", LicenceType.class);
  private static final StringEventField METADATA = EventFields.StringListValidatedByRegexp("metadata",
                                                                                          "license_metadata");
  private static final VarargEventId LICENSING = GROUP.registerVarargEvent("licencing", LICENSE_VALUE, METADATA);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
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
          result.add(EAP.metric());
          result.add(BUILD.metric(BuildType.eap));
        }
        else {
          result.add(RELEASE.metric());
          result.add(BUILD.metric(BuildType.release));
        }
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade != null) {
          // non-eap commercial version
          if (facade.isEvaluationLicense()) {
            result.add(EVALUATION.metric());
            result.add(newLicencingMetric(LicenceType.evaluation, facade.metadata));
          }
          else if (!StringUtil.isEmpty(facade.getLicensedToMessage())) {
            result.add(LICENSE.metric());
            result.add(newLicencingMetric(LicenceType.license, facade.metadata));
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
  private static MetricEvent newLicencingMetric(@NotNull LicenceType value, @Nullable String metadata) {
    List<EventPair<?>> data = new ArrayList<>();
    if (StringUtil.isNotEmpty(metadata)) {
      data.add(METADATA.with(metadata));
    }
    data.add(LICENSE_VALUE.with(value));
    return LICENSING.metric(data);
  }

  private enum LicenceType {evaluation, license}

  private enum BuildType {eap, release}
}
