// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageWithType

internal class Psi2UsageInfo2UsageAdapter(
  usageInfo: PsiUsage2UsageInfo
) : UsageInfo2UsageAdapter(usageInfo), UsageWithType {

  override fun getUsageType(): UsageType? = (usageInfo as PsiUsage2UsageInfo).psiUsage.usageType
}
