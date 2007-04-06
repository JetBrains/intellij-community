/*
 * User: anna
 * Date: 06-Apr-2007
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomScopesProvider {
  ExtensionPointName<CustomScopesProvider> CUSTOM_SCOPES_PROVIDER = ExtensionPointName.create("com.intellij.customScopesProvider");

  @NotNull
  List<NamedScope> getCustomScopes();
}