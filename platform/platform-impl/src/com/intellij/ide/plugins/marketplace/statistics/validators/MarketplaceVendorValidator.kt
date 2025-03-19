// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.validators

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule


/*
 Validates if the vendor name was provided by Marketplace and is therefore safe to report.
 */
internal class MarketplaceVendorsListValidator : CustomValidationRule() {
  override fun getRuleId(): String {
    return "mp_vendors_list"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val allowedVendors = MarketplaceRequests.getInstance().marketplaceVendorsSupplier.get()
    val isSafeToReport = allowedVendors.any {
      it.equals(data, ignoreCase = true)
    }

    return if (isSafeToReport) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}