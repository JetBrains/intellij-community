// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.libraryJar.LibraryJarStatisticsService
import com.intellij.internal.statistic.libraryUsage.LibraryUsageDescriptors

/**
 * All valid allowed library names are bundled with IDE.
 * <br/>
 * See 'library-jar-statistics.xml' and 'library-usage-statistics.xml' files.
 */
public class LibraryNameValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "used_library_name"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (AllowListHolder.allowedNames.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }

  public object AllowListHolder {
    public val allowedNames: Set<String> = (LibraryUsageDescriptors.libraryNames +
                                            LibraryJarStatisticsService.getInstance().libraryNames).toHashSet()
  }
}