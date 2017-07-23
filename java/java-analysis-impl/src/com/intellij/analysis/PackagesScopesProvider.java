/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.analysis;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PackagesScopesProvider extends CustomScopesProviderEx {
  private final NamedScope myProjectProductionScope;
  private final List<NamedScope> myScopes;

  public static PackagesScopesProvider getInstance(Project project) {
    return Extensions.findExtension(CUSTOM_SCOPES_PROVIDER, project, PackagesScopesProvider.class);
  }

  public PackagesScopesProvider() {
    myProjectProductionScope = new ProjectProductionScope();
    final NamedScope projectTestScope = new TestsScope();
    myScopes = Arrays.asList(myProjectProductionScope, projectTestScope);
  }

  @Override
  @NotNull
  public List<NamedScope> getCustomScopes() {
    return myScopes;
  }

  public NamedScope getProjectProductionScope() {
    return myProjectProductionScope;
  }
}