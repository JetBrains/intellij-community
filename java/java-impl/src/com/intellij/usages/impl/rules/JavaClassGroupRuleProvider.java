/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaClassGroupRuleProvider implements FileStructureGroupRuleProvider {
  @Override
  public UsageGroupingRule getUsageGroupingRule(@NotNull final Project project) {
    return new ClassGroupingRule();
  }
}
