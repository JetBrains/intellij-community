// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.ide.util.TipsUtilityExperiment;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TipsOfTheDayUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ui.tips", 9);

  public enum DialogType {automatically, manually}

  public static final EventId NEXT_TIP = GROUP.registerEvent("next.tip");
  public static final EventId PREVIOUS_TIP = GROUP.registerEvent("previous.tip");

  private static final EventId1<DialogType> DIALOG_SHOWN =
    GROUP.registerEvent("dialog.shown", EventFields.Enum("type", DialogType.class));

  private static final EventId2<Boolean, Boolean> DIALOG_CLOSED =
    GROUP.registerEvent("dialog.closed", EventFields.Boolean("keep_showing_before"), EventFields.Boolean("keep_showing_after"));

  private static final StringEventField ALGORITHM_FIELD =
    EventFields.String("algorithm",
                       Stream.concat(Stream.of("TOP", "MATRIX_ALS", "MATRIX_BPR", "PROB", "WIDE", "CODIS", "RANDOM", "WEIGHTS_LIN_REG",
                                                   "default_shuffle", "unknown", "ONE_TIP_SUMMER2020", "RANDOM_SUMMER2020"),
                                     Arrays.stream(TipsUtilityExperiment.values()).map(v -> v.toString()))
                         .collect(Collectors.toList()));
  private static final EventId3<String, String, String> TIP_SHOWN =
    GROUP.registerEvent("tip.shown",
                        EventFields.StringValidatedByCustomRule("filename", TipInfoValidationRule.RULE_ID),
                        ALGORITHM_FIELD,
                        EventFields.Version);

  private static final EventId2<String, Long> TIP_PERFORMED =
    GROUP.registerEvent("tip.performed",
                        EventFields.StringValidatedByCustomRule("filename", TipInfoValidationRule.RULE_ID),
                        EventFields.Long("time_passed"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void triggerTipShown(@NotNull TipAndTrickBean tip, @NotNull String algorithm, @Nullable String version) {
    TIP_SHOWN.log(tip.fileName, algorithm, version);
  }

  public static void triggerDialogShown(@NotNull DialogType type) {
    DIALOG_SHOWN.log(type);
  }

  public static void triggerDialogClosed(boolean showOnStartupBefore) {
    DIALOG_CLOSED.log(showOnStartupBefore, GeneralSettings.getInstance().isShowTipsOnStartup());
  }

  public static void triggerTipUsed(@NotNull String tipFilename, long timePassed) {
    TIP_PERFORMED.log(tipFilename, timePassed);
  }

  public static class TipInfoValidationRule extends CustomValidationRule {
    public static final String RULE_ID = "tip_info";

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return RULE_ID.equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      PluginInfo info = context.getPayload(PLUGIN_INFO);
      if (info != null) {
        return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }

      Object filename = context.eventData.get("filename");
      if (filename instanceof String) {
        TipAndTrickBean tip = TipAndTrickBean.findByFileName((String)filename);
        if (tip != null) {
          PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoByDescriptor(tip.getPluginDescriptor());
          context.setPayload(PLUGIN_INFO, pluginInfo);
          return pluginInfo.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }

      return ValidationResultType.REJECTED;
    }
  }
}
