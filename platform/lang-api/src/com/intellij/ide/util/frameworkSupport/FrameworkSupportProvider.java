// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  protected FrameworkSupportProvider(@NotNull @NonNls final String id,
                                     @NotNull final @Nls(capitalization = Nls.Capitalization.Title) String title) {
    myId = id;
    myTitle = title;
  }

  /**
   * Creates configurable for user settings (e.g. choosing version, features, ...).
   *
   * @param model Model.
   * @return Configurable.
   */
  @NotNull
  public abstract FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model);

  @NonNls
  @Nullable
  public String getUnderlyingFrameworkId() {
    return null;
  }

  @NonNls
  public String[] getPrecedingFrameworkProviderIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  public String getTitle() {
    return myTitle;
  }

  @Nullable @NonNls
  public String getGroupId() {
    return null;
  }

  public String @NotNull [] getProjectCategories() {
    return getGroupId() == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : new String[] { getGroupId() };
  }

  public FrameworkRole[] getRoles() {
    return getGroupId() == null ? FrameworkRole.UNKNOWN : new FrameworkRole[] { new FrameworkRole(getGroupId()) };
  }

  @Nullable
  public Icon getIcon() {
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

  @NotNull @NonNls
  public final String getId() {
    return myId;
  }
}
