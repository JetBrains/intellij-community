/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
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

  @NotNull
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return createConfigurable(model.getProject());
  }

  /**
   * @deprecated override {@link FrameworkSupportProvider#createConfigurable(FrameworkSupportModel)} instead
   */
  @Deprecated
  @NotNull
  public FrameworkSupportConfigurable createConfigurable(@Nullable final Project project) {
    return null;
  }

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

  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

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
