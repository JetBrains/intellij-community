// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.EnvFilesOptions;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.RunTargetValidator;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.execution.target.local.LocalTargetType;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.StoredProperty;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class RunConfigurationTypeUsagesCollector extends ProjectUsagesCollector {
  public static final String CONFIGURED_IN_PROJECT = "configured.in.project";
  public static final EventLogGroup GROUP = new EventLogGroup("run.configuration.type", 17);
  public static final StringEventField ID_FIELD = EventFields.StringValidatedByCustomRule("id", RunConfigurationUtilValidator.class);
  public static final StringEventField FACTORY_FIELD = EventFields.StringValidatedByCustomRule("factory",
                                                                                               RunConfigurationUtilValidator.class);
  private static final IntEventField COUNT_FIELD = EventFields.Int("count");
  private static final StringEventField FEATURE_NAME_FIELD = EventFields.StringValidatedByCustomRule("featureName",
                                                                                                     PluginInfoValidationRule.class);
  private static final BooleanEventField SHARED_FIELD = EventFields.Boolean("shared");
  private static final BooleanEventField EDIT_BEFORE_RUN_FIELD = EventFields.Boolean("edit_before_run");
  private static final BooleanEventField ACTIVATE_BEFORE_RUN_FIELD = EventFields.Boolean("activate_before_run");
  private static final BooleanEventField FOCUS_BEFORE_RUN_FIELD = EventFields.Boolean("focus_before_run");
  private static final BooleanEventField TEMPORARY_FIELD = EventFields.Boolean("temporary");
  private static final BooleanEventField PARALLEL_FIELD = EventFields.Boolean("parallel");
  private static final IntEventField ENV_FILES_COUNT = EventFields.Int("env_files_count");
  /**
   * Stands for the target specified for the Run Configuration.
   * <p>
   * Note that if the value is {@code null} then the <i>project default target</i> will be used for executing the run configuration. The
   * default value for the project default target is the local machine, it might be changed by the user.
   */
  private static final StringEventField TARGET_FIELD =
    EventFields.StringValidatedByCustomRule("target", RunTargetValidator.class);
  private static final ObjectEventField ADDITIONAL_FIELD = EventFields.createAdditionalDataField(GROUP.getId(), CONFIGURED_IN_PROJECT);
  private static final VarargEventId CONFIGURED_IN_PROJECT_EVENT =
    GROUP.registerVarargEvent(CONFIGURED_IN_PROJECT, COUNT_FIELD, ID_FIELD, FACTORY_FIELD, SHARED_FIELD, EDIT_BEFORE_RUN_FIELD,
                              ACTIVATE_BEFORE_RUN_FIELD, FOCUS_BEFORE_RUN_FIELD, TEMPORARY_FIELD, PARALLEL_FIELD, ADDITIONAL_FIELD,
                              TARGET_FIELD, ENV_FILES_COUNT);
  private static final VarargEventId FEATURE_USED_EVENT =
    GROUP.registerVarargEvent("feature.used", COUNT_FIELD, ID_FIELD, EventFields.PluginInfo, FEATURE_NAME_FIELD);

  private static final IntEventField TOTAL_COUNT_FIELD = EventFields.Int("total_count");
  private static final IntEventField TEMP_COUNT_FIELD = EventFields.Int("temp_count");

  private static final EventId2<Integer, Integer> TOTAL_COUNT = GROUP.registerEvent("total.configurations.registered", TOTAL_COUNT_FIELD, TEMP_COUNT_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    Object2IntMap<Template> templates = new Object2IntOpenHashMap<>();
    if (project.isDisposed()) {
      return Collections.emptySet();
    }
    RunManager runManager = RunManager.getInstance(project);
    for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
      ProgressManager.checkCanceled();
      RunConfiguration runConfiguration = settings.getConfiguration();
      final ConfigurationFactory configurationFactory = runConfiguration.getFactory();
      if (configurationFactory == null) {
        // not realistic
        continue;
      }

      final ConfigurationType configurationType = configurationFactory.getType();
      List<EventPair<?>> pairs = createFeatureUsageData(configurationType, configurationFactory);
      pairs.addAll(getSettings(settings, runConfiguration));
      final Template template = new Template(CONFIGURED_IN_PROJECT_EVENT, pairs);
      addOrIncrement(templates, template);
      collectRunConfigurationFeatures(runConfiguration, templates);
      if (runConfiguration instanceof FusAwareRunConfiguration) {
        List<EventPair<?>> additionalData = ((FusAwareRunConfiguration)runConfiguration).getAdditionalUsageData();
        pairs.add(ADDITIONAL_FIELD.with(new ObjectEventData(additionalData)));
      }
      if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
        String assignedTargetType = getAssignedTargetType(project, (TargetEnvironmentAwareRunProfile)runConfiguration);
        if (assignedTargetType != null) {
          pairs.add(TARGET_FIELD.with(assignedTargetType));
        }
      }
      if (runConfiguration instanceof EnvFilesOptions envFilesOptions) {
        pairs.add(ENV_FILES_COUNT.with(envFilesOptions.getEnvFilePaths().size()));
      }
    }
    Set<MetricEvent> metrics = new HashSet<>();
    for (Object2IntMap.Entry<Template> entry : Object2IntMaps.fastIterable(templates)) {
      metrics.add(entry.getKey().createMetricEvent(entry.getIntValue()));
    }


    final int limitingBoundary = 500; // avoid reporting extreme values
    metrics.add(TOTAL_COUNT.metric(Math.min(runManager.getAllSettings().size(), limitingBoundary),
                                   runManager.getTempConfigurationsList().size()));

    return metrics;
  }

  @Override
  protected boolean requiresReadAccess() {
    return true;
  }

  private static void addOrIncrement(Object2IntMap<Template> templates,
                                     Template template) {
    templates.mergeInt(template, 1, Math::addExact);
  }

  private static void collectRunConfigurationFeatures(RunConfiguration runConfiguration,
                                                      Object2IntMap<Template> templates) {
    if (runConfiguration instanceof RunConfigurationBase) {
      PluginInfo info = PluginInfoDetectorKt.getPluginInfo(runConfiguration.getClass());
      if (!info.isSafeToReport()) return;
      Object state = ((RunConfigurationBase<?>)runConfiguration).getState();
      if (state instanceof RunConfigurationOptions runConfigurationOptions) {
        List<StoredProperty<Object>> properties = runConfigurationOptions.__getProperties();
        for (StoredProperty<Object> property : properties) {
          String name = property.getName();
          if (name == null || name.equals("isAllowRunningInParallel") || name.equals("isNameGenerated")) continue;
          Object value = property.getValue(runConfigurationOptions);
          boolean featureUsed;
          if (value instanceof Boolean) {
            featureUsed = (Boolean)value;
          }
          else if (value instanceof String) {
            featureUsed = StringUtil.isNotEmpty((String)value);
          }
          else if (value instanceof Collection) {
            featureUsed = ((Collection<?>)value).size() > 0;
          }
          else if (value instanceof Map) {
            featureUsed = ((Map<?, ?>)value).size() > 0;
          }
          else {
            continue;
          }
          if (featureUsed) {
            List<EventPair<?>> pairs = new ArrayList<>();
            pairs.add(ID_FIELD.with(runConfiguration.getType().getId()));
            pairs.add(EventFields.PluginInfo.with(info));
            pairs.add(FEATURE_NAME_FIELD.with(name));
            addOrIncrement(templates, new Template(FEATURE_USED_EVENT, pairs));
          }
        }
      }
    }
  }

  public static @NotNull List<EventPair<?>> createFeatureUsageData(@NotNull ConfigurationType configuration,
                                                                   @Nullable ConfigurationFactory factory) {
    final String id = configuration instanceof UnknownConfigurationType ? "unknown" : configuration.getId();
    List<EventPair<?>> pairs = new ArrayList<>();
    pairs.add(ID_FIELD.with(id));
    if (factory != null && configuration.getConfigurationFactories().length > 1) {
      pairs.add(FACTORY_FIELD.with(factory.getId()));
    }
    return pairs;
  }

  private static @NotNull List<EventPair<Boolean>> getSettings(@NotNull RunnerAndConfigurationSettings settings,
                                                                    @NotNull RunConfiguration runConfiguration) {
    return List.of(SHARED_FIELD.with(settings.isShared()),
                   EDIT_BEFORE_RUN_FIELD.with(settings.isEditBeforeRun()),
                   ACTIVATE_BEFORE_RUN_FIELD.with(settings.isActivateToolWindowBeforeRun()),
                   FOCUS_BEFORE_RUN_FIELD.with(settings.isFocusToolWindowBeforeRun()),
                   PARALLEL_FIELD.with(runConfiguration.isAllowRunningInParallel()),
                   TEMPORARY_FIELD.with(settings.isTemporary()));
  }

  private static final class Template {
    private final VarargEventId myEventId;
    private final List<EventPair<?>> myEventPairs;

    private Template(VarargEventId id,
                     List<EventPair<?>> pairs) {
      myEventId = id;
      myEventPairs = pairs;
    }

    private @NotNull MetricEvent createMetricEvent(int count) {
      myEventPairs.add(COUNT_FIELD.with(count));
      return myEventId.metric(myEventPairs);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Template template = (Template)o;
      return Objects.equals(myEventId, template.myEventId) &&
             Objects.equals(myEventPairs, template.myEventPairs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myEventId, myEventPairs);
    }
  }

  public static final class RunConfigurationUtilValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "run_config_id";
    }

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return getRuleId().equals(ruleId) || "run_config_factory".equals(ruleId);
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data) || "unknown".equals(data)) return ValidationResultType.ACCEPTED;

      final String configurationId = getEventDataField(context, ID_FIELD.getName());
      final String factoryId = getEventDataField(context, FACTORY_FIELD.getName());
      if (configurationId == null) {
        return ValidationResultType.REJECTED;
      }

      if (StringUtil.equals(data, configurationId) || StringUtil.equals(data, factoryId)) {
        final Pair<ConfigurationType, ConfigurationFactory> configurationAndFactory =
          findConfigurationAndFactory(configurationId, factoryId);

        final ConfigurationType configuration = configurationAndFactory.getFirst();
        final ConfigurationFactory factory = configurationAndFactory.getSecond();
        if (configuration != null && (StringUtil.isEmpty(factoryId) || factory != null)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(configuration.getClass());
          context.setPayload(PLUGIN_INFO, info);
          return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }

    private static @NotNull Pair<ConfigurationType, ConfigurationFactory> findConfigurationAndFactory(@NotNull String configurationId,
                                                                                                      @Nullable String factoryId) {
      final ConfigurationType configuration = findRunConfigurationById(configurationId);
      if (configuration == null) {
        return Pair.empty();
      }

      final ConfigurationFactory factory = StringUtil.isEmpty(factoryId) ? null : findFactoryById(configuration, factoryId);
      return Pair.create(configuration, factory);
    }

    private static @Nullable ConfigurationType findRunConfigurationById(@NotNull String configuration) {
      final ConfigurationType[] types = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions();
      for (ConfigurationType type : types) {
        if (StringUtil.equals(type.getId(), configuration)) {
          return type;
        }
      }
      return null;
    }

    private static @Nullable ConfigurationFactory findFactoryById(@NotNull ConfigurationType configuration, @NotNull String factoryId) {
      for (ConfigurationFactory factory : configuration.getConfigurationFactories()) {
        if (StringUtil.equals(factory.getId(), factoryId)) {
          return factory;
        }
      }
      return null;
    }
  }

  /**
   * The logged string type for the local machine target. Stands for {@link LocalTargetType#LOCAL_TARGET_NAME} target identifier.
   * <p>
   * Just for the reason that {@code "local"} looks prettier than {@code "@@@LOCAL@@@"}.
   */
  static final String LOCAL_TYPE_ID = "local";

  /**
   * <ul>
   * <li>{@code null} stands for the project default target;</li>
   * <li>{@code "local"} stands for the explicitly selected local machine configuration;</li>
   * <li>other values stands for the specific target types.</li>
   * </ul>
   */
  private static @Nullable String getAssignedTargetType(@NotNull Project project,
                                                        @NotNull TargetEnvironmentAwareRunProfile runConfiguration) {
    String assignedTargetName = runConfiguration.getDefaultTargetName();
    if (LocalTargetType.LOCAL_TARGET_NAME.equals(assignedTargetName)) {
      return LOCAL_TYPE_ID;
    }
    else if (assignedTargetName != null) {
      TargetEnvironmentConfiguration target = TargetEnvironmentsManager.getInstance(project).getTargets().findByName(assignedTargetName);
      if (target != null) {
        return target.getTypeId();
      }
    }
    return null;
  }
}
