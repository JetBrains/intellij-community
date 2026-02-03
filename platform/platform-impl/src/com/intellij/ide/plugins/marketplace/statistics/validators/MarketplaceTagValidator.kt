// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.validators

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

/*
 Validates if the tag name was provided by Marketplace and is therefore safe to report.
 */
internal class MarketplaceTagValidator : CustomValidationRule() {
  override fun getRuleId(): String {
    return "mp_tags_list"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val allowedTags = MarketplaceRequests.getInstance().marketplaceTagsSupplier.get()
    val isSafeToReport = allowedTags.any {
      it.equals(data, ignoreCase = true)
    }

    return if (isSafeToReport) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}