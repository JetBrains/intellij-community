// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class FacetTypeUsageCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("module.facets", 6);

  private static final EventField<String> FACET_TYPE = EventFields.StringValidatedByCustomRule("facet", FacetTypeUtilValidator.class);
  private static final VarargEventId MODULE = GROUP.registerVarargEvent(
    "module.with.facet", FACET_TYPE, EventFields.PluginInfo
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    final Set<String> facets = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        facets.add(facet.getType().getStringId());
      }
    }
    return ContainerUtil.map2Set(
      facets, facet -> MODULE.metric(FACET_TYPE.with(facet))
    );
  }

  public static final class FacetTypeUtilValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "facets_type";
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if ("invalid".equals(data) || isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      final FacetType facet = findFacetById(data);
      if (facet == null) {
        return ValidationResultType.REJECTED;
      }
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(facet.getClass());
      context.setPayload(PLUGIN_INFO, info);
      return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }

    private static @Nullable FacetType findFacetById(@NotNull String data) {
      final FacetType[] facets = FacetType.EP_NAME.getExtensions();
      for (FacetType facet : facets) {
        if (StringUtil.equals(facet.getStringId(), data)) {
          return facet;
        }
      }
      return null;
    }
  }
}
