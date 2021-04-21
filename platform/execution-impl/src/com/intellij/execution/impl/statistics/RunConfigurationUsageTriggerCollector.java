// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.TargetEnvironmentsManager;
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

import static com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.createFeatureUsageData;

public final class RunConfigurationUsageTriggerCollector extends CounterUsagesCollector {
  public static final String GROUP_NAME = "run.configuration.exec";
  private static final EventLogGroup GROUP = new EventLogGroup(GROUP_NAME, 62);
  private static final ObjectEventField ADDITIONAL_FIELD = EventFields.createAdditionalDataField(GROUP_NAME, "started");
  private static final StringEventField EXECUTOR = EventFields.StringValidatedByCustomRule("executor", "run_config_executor");
  private static final StringEventField TARGET =
    EventFields.StringValidatedByCustomRule("target", RunConfigurationUsageTriggerCollector.RunTargetValidator.RULE_ID);
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
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      String defaultTargetName = ((TargetEnvironmentAwareRunProfile)runConfiguration).getDefaultTargetName();
      if (defaultTargetName != null) {
        TargetEnvironmentConfiguration target = TargetEnvironmentsManager.getInstance(project).getTargets().findByName(defaultTargetName);
        if (target != null) {
          eventPairs.add(TARGET.with(target.getTypeId()));
        }
      }
    }
    return eventPairs;
  }

  public static void logProcessFinished(@Nullable StructuredIdeActivity activity,
                                        RunConfigurationFinishType finishType) {
    if (activity != null) {
      activity.finished(() -> Collections.singletonList(FINISH_TYPE.with(finishType)));
    }
  }

  public static class RunConfigurationExecutorUtilValidator extends CustomValidationRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "run_config_executor".equals(ruleId);
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

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return RULE_ID.equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      for (TargetEnvironmentType<?> type : TargetEnvironmentType.EXTENSION_NAME.getExtensions()) {
        if (StringUtil.equals(type.getId(), data)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(type.getClass());
          return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }

  public enum RunConfigurationFinishType {FAILED_TO_START, UNKNOWN}
}
