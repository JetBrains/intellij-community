/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 06-Apr-2007
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomScopesProvider {
  ExtensionPointName<CustomScopesProvider> CUSTOM_SCOPES_PROVIDER = ExtensionPointName.create("com.intellij.customScopesProvider");

  @NotNull
  List<NamedScope> getCustomScopes();

  @NotNull
  default List<NamedScope> getFilteredScopes() {
    CustomScopesFilter[] filters = CustomScopesFilter.EP_NAME.getExtensions();
    return ContainerUtil.filter(getCustomScopes(), scope -> {
      for (CustomScopesFilter filter : filters) {
        if (filter.excludeScope(scope)) return false;
      }
      return true;
    });
  }
}