/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Extend {@link FrameworkSupportProviderBase} for general and {@link com.intellij.facet.ui.FacetBasedFrameworkSupportProvider} for
 * {@link com.intellij.facet.Facet}-based framework support in your plugin.
 *
 * @author nik
 */
public abstract class FrameworkSupportProvider {
  public static final ExtensionPointName<FrameworkSupportProvider> EXTENSION_POINT = ExtensionPointName.create("com.intellij.frameworkSupport");
  private final String myId;
  private final String myTitle;

  protected FrameworkSupportProvider(final @NonNls @NotNull String id, final @NotNull String title) {
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
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String getTitle() {
    return myTitle;
  }

  @Nullable @NonNls
  public String getGroupId() {
    return null;
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

  @NotNull @NonNls
  public final String getId() {
    return myId;
  }
}
