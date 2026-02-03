// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageWithType

internal class PsiRename2UsageInfo2UsageAdapter(
  usageInfo: PsiRenameUsage2UsageInfo,
) : UsageInfo2UsageAdapter(usageInfo), UsageWithType {

  override fun getUsageType(): UsageType? = (usageInfo as PsiRenameUsage2UsageInfo).renameUsage.usageType

  override fun isReadOnly(): Boolean {
    return super.isReadOnly() || (usageInfo as PsiRenameUsage2UsageInfo).isReadOnly
  }
}
