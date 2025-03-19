// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageWithType

internal class PsiRename2ReadWriteAccessUsageInfo2UsageAdapter(
  usageInfo: PsiRenameUsage2UsageInfo, access: ReadWriteAccessDetector.Access
) : ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access), UsageWithType {

  override fun getUsageType(): UsageType? = (usageInfo as PsiRenameUsage2UsageInfo).renameUsage.usageType

  override fun isReadOnly(): Boolean {
    return super.isReadOnly() || (usageInfo as PsiRenameUsage2UsageInfo).renameUsage !is ModifiableRenameUsage
  }
}
