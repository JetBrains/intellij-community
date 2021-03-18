// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetTypeIdRule extends CustomValidationRule {
  static final String TARGET_TYPE_ID_RULE_ID = "target_type_id";

  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return TARGET_TYPE_ID_RULE_ID.equals(ruleId);
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String typeId,
                                                     @NotNull EventContext context) {
    TargetEnvironmentType<?> typeById = TargetEnvironmentType.EXTENSION_NAME.findFirstSafe(type -> typeId.equals(type.getId()));
    if (typeById == null) return ValidationResultType.REJECTED;
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(typeById.getClass());
    if (pluginInfo.isSafeToReport()) {
      return ValidationResultType.ACCEPTED;
    }
    else {
      return ValidationResultType.REJECTED;
    }
  }
}
