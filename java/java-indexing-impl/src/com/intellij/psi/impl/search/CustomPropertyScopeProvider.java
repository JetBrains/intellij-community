// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Allows enlarging searching scope for properties and their simple accessors
 * 
 * @see SimpleAccessorReferenceSearcher
 */
public interface CustomPropertyScopeProvider {
  ExtensionPointName<CustomPropertyScopeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.customPropertyScopeProvider");

  @NotNull
  SearchScope getScope(@NotNull Project project);
}
