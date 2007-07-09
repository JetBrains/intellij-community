package com.intellij.usages.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.Nullable;

public interface FileStructureGroupRuleProvider {
  ExtensionPointName<FileStructureGroupRuleProvider> EP_NAME = new ExtensionPointName<FileStructureGroupRuleProvider>("com.intellij.fileStructureGroupRuleProvider");

  @Nullable
  UsageGroupingRule getUsageGroupingRule();
}
