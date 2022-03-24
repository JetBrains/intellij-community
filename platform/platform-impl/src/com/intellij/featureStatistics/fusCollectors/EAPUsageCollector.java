// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.idea.Main;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class EAPUsageCollector extends ApplicationUsagesCollector implements AllowedDuringStartupCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("user.advanced.info", 4);
  private static final EventId1<BuildType> BUILD = GROUP.registerEvent("build", EventFields.Enum("value", BuildType.class));
  private static final EnumEventField<LicenceType> LICENSE_VALUE = EventFields.Enum("value", LicenceType.class);
  private static final StringEventField METADATA = EventFields.StringValidatedByRegexp("metadata", "license_metadata");
  private static final BooleanEventField IS_JB_TEAM = EventFields.Boolean("is_jb_team");
  private static final VarargEventId LICENSING = GROUP.registerVarargEvent("licencing", LICENSE_VALUE, METADATA, IS_JB_TEAM);

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
          result.add(BUILD.metric(BuildType.eap));
        }
        else {
          result.add(BUILD.metric(BuildType.release));
        }
        final LicensingFacade facade = LicensingFacade.getInstance();
        if (facade != null) {
          // non-eap commercial version
          if (facade.isEvaluationLicense()) {
            result.add(newLicencingMetric(LicenceType.evaluation, facade));
          }
          else if (!StringUtil.isEmpty(facade.getLicensedToMessage())) {
            result.add(newLicencingMetric(LicenceType.license, facade));
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
  private static MetricEvent newLicencingMetric(@NotNull LicenceType value, @NotNull LicensingFacade licensingFacade) {
    List<EventPair<?>> data = new ArrayList<>();
    String licensedToMessage = licensingFacade.getLicensedToMessage();
    if (licensedToMessage != null && licensedToMessage.contains("JetBrains Team")) {
      data.add(IS_JB_TEAM.with(true));
    }
    String metadata = licensingFacade.metadata;
    if (StringUtil.isNotEmpty(metadata)) {
      data.add(METADATA.with(metadata));
    }
    data.add(LICENSE_VALUE.with(value));
    return LICENSING.metric(data);
  }

  private enum LicenceType {evaluation, license}

  private enum BuildType {eap, release}
}
