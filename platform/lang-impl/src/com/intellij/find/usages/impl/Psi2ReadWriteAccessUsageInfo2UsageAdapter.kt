// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageWithType

internal class Psi2ReadWriteAccessUsageInfo2UsageAdapter(
  usageInfo: PsiUsage2UsageInfo, access: ReadWriteAccessDetector.Access
) : ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access), UsageWithType {

  override fun getUsageType(): UsageType? = (usageInfo as PsiUsage2UsageInfo).psiUsage.usageType
}
