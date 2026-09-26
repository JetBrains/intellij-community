// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies;

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
@InternalIgnoreDependencyViolation
public final class TestScopeProvider extends CustomScopesProviderEx {
  public static TestScopeProvider getInstance(@NotNull Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(TestScopeProvider.class, project);
  }

  @Override
  public @NotNull List<NamedScope> getCustomScopes() {
    return Collections.singletonList(TestsScope.INSTANCE);
  }
}
