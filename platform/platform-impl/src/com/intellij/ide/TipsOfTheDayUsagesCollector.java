// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TipsOfTheDayUsagesCollector {
  private static final String GROUP_ID = "ui.tips";
  private static final String NO_FEATURE_ID = "no.feature.id";

  public static void trigger(String feature) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, feature);
  }

  public static void triggerShow(@NotNull String type) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, "dialog.shown", new FeatureUsageData().addData("type", type));
  }

  public static void triggerTipShown(@NotNull TipAndTrickBean tip) {
    FeatureUsageData usageData = new FeatureUsageData();

    String featureId = tip.featureId;
    usageData.addData("feature_id", featureId != null ? featureId : NO_FEATURE_ID);
    usageData.addData("filename", tip.fileName);

    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, "tip.shown", usageData);
  }

  public static class TipInfoWhiteListRule extends CustomWhiteListRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "tip_info".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (NO_FEATURE_ID.equals(data) || isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;
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
