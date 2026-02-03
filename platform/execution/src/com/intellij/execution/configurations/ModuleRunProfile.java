// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.configurations;

import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface ModuleRunProfile extends RunProfileWithCompileBeforeLaunchOption, SearchScopeProvidingRunProfile {

  @Override
  default @Nullable GlobalSearchScope getSearchScope() {
    return ExecutionSearchScopes.executionScope(Arrays.asList(getModules()));
  }
}
