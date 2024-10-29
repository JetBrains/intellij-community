// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.idea.AppMode;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public final class EAPUsageCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("user.advanced.info", 6);
  private static final EventId1<BuildType> BUILD = GROUP.registerEvent("build", EventFields.Enum("value", BuildType.class));
  private static final EnumEventField<LicenceType> LICENSE_VALUE = EventFields.Enum("value", LicenceType.class);
  private static final StringEventField METADATA = EventFields.StringValidatedByRegexpReference("metadata", "license_metadata");
  private static final EventField<String> LOGIN_HASH = EventFields.AnonymizedField("login_hash");
  private static final BooleanEventField IS_JB_TEAM = EventFields.Boolean("is_jb_team");
  private static final VarargEventId LICENSING = GROUP.registerVarargEvent("licencing", LICENSE_VALUE, METADATA, LOGIN_HASH, IS_JB_TEAM);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    return collectMetrics();
  }

  private static @NotNull Set<MetricEvent> collectMetrics() {
    try {
      if (!AppMode.isHeadless()) {
        final Set<MetricEvent> result = new HashSet<>();
        if (ApplicationInfo.getInstance().isEAP()) {
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

  @ApiStatus.Internal
  public static boolean isJBTeam() {
    LicensingFacade licensingFacade = LicensingFacade.getInstance();
    if (licensingFacade == null) return false;
    String licensedToMessage = licensingFacade.getLicensedToMessage();
    return licensedToMessage != null && licensedToMessage.contains("JetBrains Team");
  }

  private static @NotNull MetricEvent newLicencingMetric(@NotNull LicenceType value, @NotNull LicensingFacade licensingFacade) {
    List<EventPair<?>> data = new ArrayList<>();

    if (isJBTeam()) {
      data.add(IS_JB_TEAM.with(true));
    }
    String metadata = licensingFacade.metadata;
    if (StringUtil.isNotEmpty(metadata)) {
      data.add(METADATA.with(metadata));
    }
    data.add(LICENSE_VALUE.with(value));
    data.add(LOGIN_HASH.with(licensingFacade.getLicenseeEmail()));
    return LICENSING.metric(data);
  }

  private enum LicenceType {evaluation, license}

  private enum BuildType {eap, release}
}
