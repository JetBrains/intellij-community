package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 * @deprecated todo remove in IDEA 16
 * use com.intellij.psi.ResolveScopeProvider
 */
public abstract class SdkResolveScopeProvider {
  public static final ExtensionPointName<SdkResolveScopeProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.sdkResolveScopeProvider");

  @Nullable
  public abstract GlobalSearchScope getScope(@NotNull Project project, @NotNull JdkOrderEntry entry);
}
