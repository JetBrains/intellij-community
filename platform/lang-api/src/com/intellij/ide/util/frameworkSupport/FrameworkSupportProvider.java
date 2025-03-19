// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Extend {@link FrameworkSupportProviderBase} for general and {@link com.intellij.facet.ui.FacetBasedFrameworkSupportProvider} for
 * {@link com.intellij.facet.Facet}-based framework support in your plugin.
 */
public abstract class FrameworkSupportProvider {
  public static final ExtensionPointName<FrameworkSupportProvider> EXTENSION_POINT = ExtensionPointName.create("com.intellij.frameworkSupport");
  private final String myId;
  private final @Nls(capitalization = Nls.Capitalization.Title) String myTitle;

  protected FrameworkSupportProvider(final @NotNull @NonNls String id,
                                     final @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    myId = id;
    myTitle = title;
  }

  /**
   * Creates configurable for user settings (e.g. choosing version, features, ...).
   *
   * @param model Model.
   * @return Configurable.
   */
  public abstract @NotNull FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model);

  public @NonNls @Nullable String getUnderlyingFrameworkId() {
    return null;
  }

  public @NonNls String[] getPrecedingFrameworkProviderIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  public @Nls(capitalization = Nls.Capitalization.Title) String getTitle() {
    return myTitle;
  }

  public @Nullable @NonNls String getGroupId() {
    return null;
  }

  public String @NotNull [] getProjectCategories() {
    return getGroupId() == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : new String[] { getGroupId() };
  }

  public FrameworkRole[] getRoles() {
    return getGroupId() == null ? FrameworkRole.UNKNOWN : new FrameworkRole[] { new FrameworkRole(getGroupId()) };
  }

  public @Nullable Icon getIcon() {
    return null;
  }

  public abstract boolean isEnabledForModuleType(@NotNull ModuleType moduleType);

  public boolean isEnabledForModuleBuilder(@NotNull ModuleBuilder builder) {
    return isEnabledForModuleType(builder.getModuleType());
  }

  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    return false;
  }

  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return isSupportAlreadyAdded(module);
  }

  public final @NotNull @NonNls String getId() {
    return myId;
  }
}
