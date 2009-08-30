package com.intellij.psi.impl.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;

public interface CustomPropertyScopeProvider {
  ExtensionPointName<CustomPropertyScopeProvider> EP_NAME = new ExtensionPointName<CustomPropertyScopeProvider>("com.intellij.customPropertyScopeProvider");

  SearchScope getScope(final Project project);
}
