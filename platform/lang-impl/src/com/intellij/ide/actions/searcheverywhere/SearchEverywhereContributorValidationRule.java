// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class SearchEverywhereContributorValidationRule extends CustomValidationRule {

  private static final Map<String, Boolean> ourContributorsMap = new HashMap<>();

  @Override
  public @NotNull String getRuleId() {
    return "se_contributor";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    Boolean isJB = ourContributorsMap.get(data);
    return isJB == null ? ValidationResultType.REJECTED
           : isJB.booleanValue() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
  }

  static void updateContributorsMap(Collection<? extends SearchEverywhereContributor<?>> contributors) {
    ourContributorsMap.clear();
    contributors.forEach(contributor -> {
      Class<? extends SearchEverywhereContributor> clazz = contributor.getClass();
      PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
      ourContributorsMap.put(contributor.getSearchProviderId(), pluginInfo.isDevelopedByJetBrains());
    });
  }
}
