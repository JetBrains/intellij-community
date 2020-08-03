// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TipsOfTheDayUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ui.tips", 4);

  public enum DialogType { automatically, manually }

  public static final EventId NEXT_TIP = GROUP.registerEvent("next.tip");
  public static final EventId PREVIOUS_TIP = GROUP.registerEvent("previous.tip");
  public static final EventId1<DialogType> DIALOG_SHOWN =
    GROUP.registerEvent("dialog.shown", EventFields.Enum("type", DialogType.class));

  private static final EventId3<String, String, String> TIP_SHOWN =
    GROUP.registerEvent("tip.shown",
                        EventFields.String("filename").withCustomRule("tip_info"),
                        EventFields.String("algorithm").withCustomEnum("tips_order_algorithm"),
                        EventFields.Version);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void triggerTipShown(@NotNull TipAndTrickBean tip, @NotNull String algorithm, @Nullable String version) {
    TIP_SHOWN.log(tip.fileName, algorithm, version);
  }

  public static class TipInfoValidationRule extends CustomValidationRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "tip_info".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      PluginInfo info = context.pluginInfo;
      if (info != null) {
        return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }

      Object filename = context.eventData.get("filename");
      if (filename instanceof String) {
        TipAndTrickBean tip = TipAndTrickBean.findByFileName((String)filename);
        if (tip != null) {
          PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoByDescriptor(tip.getPluginDescriptor());
          context.setPluginInfo(pluginInfo);
          return pluginInfo.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }

      return ValidationResultType.REJECTED;
    }
  }
}
