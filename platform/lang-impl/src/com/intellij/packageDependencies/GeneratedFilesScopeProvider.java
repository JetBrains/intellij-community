// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies;

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.psi.search.scope.GeneratedFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
@InternalIgnoreDependencyViolation
public final class GeneratedFilesScopeProvider implements CustomScopesProvider {
  @Override
  public @NotNull List<NamedScope> getCustomScopes() {
    return List.of(GeneratedFilesScope.INSTANCE);
  }
}
