// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FavoriteTypeValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "favorite_type"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val provider = FavoriteNodeProvider.EP_NAME.findFirstSafe { it.favoriteTypeId == data }
    if (provider == null) return ValidationResultType.REJECTED
    val pluginInfo = getPluginInfo(provider.javaClass)
    return if (pluginInfo.isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}
