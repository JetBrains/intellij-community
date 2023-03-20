// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.target.*;
import com.intellij.internal.statistic.IdeActivityDefinition;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.LOCAL_TYPE_ID;
import static com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.createFeatureUsageData;

public final class RunConfigurationUsageTriggerCollector extends CounterUsagesCollector {
  public static final String GROUP_NAME = "run.configuration.exec";
  private static final EventLogGroup GROUP = new EventLogGroup(GROUP_NAME, 69);
  public static final IntEventField ALTERNATIVE_JRE_VERSION = EventFields.Int("alternative_jre_version");
  private static final ObjectEventField ADDITIONAL_FIELD = EventFields.createAdditionalDataField(GROUP_NAME, "started");
  private static final StringEventField EXECUTOR = EventFields.StringValidatedByCustomRule("executor",
                                                                                           RunConfigurationExecutorUtilValidator.class);
  /**
   * The type of the target the run configuration is being executed with. {@code null} stands for the local machine target.
   * <p>
   * Takes into the account the project default target and uses its value if the "project default target" is specified for the run
   * configuration.
   */
  private static final StringEventField TARGET =
    EventFields.StringValidatedByCustomRule("target", RunTargetValidator.class);
  private static final EnumEventField<RunConfigurationFinishType> FINISH_TYPE =
    EventFields.Enum("finish_type", RunConfigurationFinishType.class);

  private static final IdeActivityDefinition ACTIVITY_GROUP = GROUP.registerIdeActivity(null,
                                                                                        new EventField<?>[]{ADDITIONAL_FIELD, EXECUTOR,
                                                                                          TARGET,
                                                                                          RunConfigurationTypeUsagesCollector.FACTORY_FIELD,
                                                                                          RunConfigurationTypeUsagesCollector.ID_FIELD,
                                                                                          EventFields.PluginInfo},
                                                                                        new EventField<?>[]{FINISH_TYPE});

  public static final VarargEventId UI_SHOWN_STAGE = ACTIVITY_GROUP.registerStage("ui.shown");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  public static StructuredIdeActivity trigger(@NotNull Project project,
                                              @NotNull ConfigurationFactory factory,
                                              @NotNull Executor executor,
                                              @Nullable RunConfiguration runConfiguration) {
    return ACTIVITY_GROUP
      .startedAsync(project, () -> ReadAction.nonBlocking(() -> buildContext(project, factory, executor, runConfiguration))
        .expireWith(project)
        .submit(NonUrgentExecutor.getInstance()));
  }

  private static @NotNull List<EventPair<?>> buildContext(@NotNull Project project,
                                                          @NotNull ConfigurationFactory factory,
                                                          @NotNull Executor executor,
                                                          @Nullable RunConfiguration runConfiguration) {
    final ConfigurationType configurationType = factory.getType();
    List<EventPair<?>> eventPairs = createFeatureUsageData(configurationType, factory);
    ExecutorGroup<?> group = ExecutorGroup.getGroupIfProxy(executor);
    eventPairs.add(EXECUTOR.with(group != null ? group.getId() : executor.getId()));
    if (runConfiguration instanceof FusAwareRunConfiguration) {
      List<EventPair<?>> additionalData = ((FusAwareRunConfiguration)runConfiguration).getAdditionalUsageData();
      ObjectEventData objectEventData = new ObjectEventData(additionalData);
      eventPairs.add(ADDITIONAL_FIELD.with(objectEventData));
    }
    String targetTypeId = getTargetTypeId(project, runConfiguration);
    if (targetTypeId != null) {
      eventPairs.add(TARGET.with(targetTypeId));
    }
    return eventPairs;
  }

  private static @Nullable String getTargetTypeId(@NotNull Project project, @Nullable RunConfiguration runConfiguration) {
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      String assignedTargetName = ((TargetEnvironmentAwareRunProfile)runConfiguration).getDefaultTargetName();
      TargetEnvironmentConfiguration effectiveTargetConfiguration =
        TargetEnvironmentConfigurations.getEffectiveConfiguration(assignedTargetName, project);
      if (effectiveTargetConfiguration != null) {
        return effectiveTargetConfiguration.getTypeId();
      }
    } else if (runConfiguration instanceof ImplicitTargetAwareRunProfile) {
      TargetEnvironmentType<?> targetType = ((ImplicitTargetAwareRunProfile)runConfiguration).getTargetType();
      if (targetType != null) {
        return targetType.getId();
      }
    }
    return null;
  }

  public static void logProcessFinished(@Nullable StructuredIdeActivity activity,
                                        RunConfigurationFinishType finishType) {
    if (activity != null) {
      activity.finished(() -> Collections.singletonList(FINISH_TYPE.with(finishType)));
    }
  }

  public static class RunConfigurationExecutorUtilValidator extends CustomValidationRule {
    @NotNull
    @Override
    public String getRuleId() {
      return "run_config_executor";
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
        if (StringUtil.equals(executor.getId(), data)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(executor.getClass());
          return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }

  public static class RunTargetValidator extends CustomValidationRule {
    public static final String RULE_ID = "run_target";

    @NotNull
    @Override
    public String getRuleId() {
      return RULE_ID;
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (LOCAL_TYPE_ID.equals(data)) {
        return ValidationResultType.ACCEPTED;
      }
      for (TargetEnvironmentType<?> type : TargetEnvironmentType.EXTENSION_NAME.getExtensions()) {
        if (StringUtil.equals(type.getId(), data)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(type.getClass());
          return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }

  public enum RunConfigurationFinishType {FAILED_TO_START, UNKNOWN, TERMINATED}
}
