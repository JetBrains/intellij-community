// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.advanced.AdvancedSettingBean;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.*;

@ApiStatus.Internal
public final class RegistryApplicationUsagesCollector extends ApplicationUsagesCollector {
  public static final String DISABLE_INTELLIJ_PROJECT_ANALYTICS = "ide.disable.intellij.project.analytics";

  private static final EventLogGroup GROUP = new EventLogGroup("platform.registry", 5);
  private static final StringEventField REGISTRY_KEY = EventFields.StringValidatedByCustomRule("id", RegistryUtilValidator.class);

  private static final VarargEventId REGISTRY = GROUP.registerVarargEvent("registry", REGISTRY_KEY, EventFields.PluginInfo);
  private static final VarargEventId EXPERIMENT = GROUP.registerVarargEvent("experiment", REGISTRY_KEY, EventFields.PluginInfo);
  private static final VarargEventId ADVANCED_SETTING = GROUP.registerVarargEvent("advanced.setting", REGISTRY_KEY, EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    return getChangedValuesUsages();
  }

  static @NotNull Set<MetricEvent> getChangedValuesUsages() {
    final Set<MetricEvent> registry = Registry.getAll().stream()
      .filter(key -> key.isChangedFromDefault() && !StringUtil.equals(key.getKey(), DISABLE_INTELLIJ_PROJECT_ANALYTICS))
      .map(key -> REGISTRY.metric(REGISTRY_KEY.with(key.getKey())))
      .collect(Collectors.toSet());

    final Set<MetricEvent> experiments = Experiments.EP_NAME.getExtensionList().stream()
      .filter(f -> Experiments.getInstance().isFeatureEnabled(f.id))
      .map(f -> EXPERIMENT.metric(REGISTRY_KEY.with(f.id)))
      .collect(Collectors.toSet());

    final Set<MetricEvent> advancedSettings = AdvancedSettingBean.EP_NAME.getExtensionList().stream()
      .filter(f -> ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).isNonDefault(f.id))
      .map(f -> ADVANCED_SETTING.metric(REGISTRY_KEY.with(f.id)))
      .collect(Collectors.toSet());

    final Set<MetricEvent> result = new HashSet<>(registry);
    result.addAll(experiments);
    result.addAll(advancedSettings);
    return result;
  }

  public static final class RegistryUtilValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "registry_key";
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      final ExperimentalFeature feature = findFeatureById(data);
      if (feature != null) {
        final PluginInfo info = getPluginInfo(feature.getClass());
        context.setPayload(PLUGIN_INFO, info);
        return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }

      for (AdvancedSettingBean extension : AdvancedSettingBean.EP_NAME.getExtensionList()) {
        if (extension.id.equals(data)) {
          PluginDescriptor descriptor = extension.getPluginDescriptor();
          if (descriptor == null) return ValidationResultType.REJECTED;
          final PluginInfo info = getPluginInfoByDescriptor(descriptor);
          context.setPayload(PLUGIN_INFO, info);
          return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }


      PluginInfo info = getPluginInfoByRegistry(Registry.get(data));
      context.setPayload(PLUGIN_INFO, info);
      return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }

    private static @NotNull PluginInfo getPluginInfoByRegistry(@NotNull RegistryValue value) {
      String pluginId = value.getPluginId();
      return pluginId != null ? getPluginInfoById(PluginId.getId(pluginId)) : getPlatformPlugin();
    }

    private static @Nullable ExperimentalFeature findFeatureById(@NotNull String featureId) {
      for (ExperimentalFeature feature : Experiments.EP_NAME.getExtensionList()) {
        if (Objects.equals(feature.id, featureId)) {
          return feature;
        }
      }
      return null;
    }
  }
}
