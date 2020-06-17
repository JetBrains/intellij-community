// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.internal.statistic.IdeActivity;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.createFeatureUsageData;

public final class RunConfigurationUsageTriggerCollector {
  public static final String GROUP = "run.configuration.exec";
  private static final ObjectEventField ADDITIONAL_FIELD = EventFields.createAdditionalDataField(GROUP, "started");
  private static final StringEventField EXECUTOR = EventFields.String("executor").withCustomRule("run_config_executor");

  @NotNull
  public static IdeActivity trigger(@NotNull Project project,
                                    @NotNull ConfigurationFactory factory,
                                    @NotNull Executor executor,
                                    @Nullable RunConfiguration runConfiguration) {
    final ConfigurationType configurationType = factory.getType();
    return new IdeActivity(project, GROUP).startedWithData(data -> {
      List<EventPair> eventPairs = createFeatureUsageData(configurationType, factory);
      eventPairs.add(EXECUTOR.with(executor.getId()));
      eventPairs.forEach(pair -> pair.addData(data));
    });
  }

  public static class RunConfigurationExecutorUtilValidator extends CustomWhiteListRule {

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
}
