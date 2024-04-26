// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.NotNull;


public final class NonJavaFileGroupRuleProvider implements FileStructureGroupRuleProvider {
  @Override
  public UsageGroupingRule getUsageGroupingRule(final @NotNull Project project) {
    return new NonJavaFileGroupingRule(project);
  }
}
