// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleTypeUtils.WEB_MODULE_ENTITY_TYPE_ID_NAME;


public abstract class WebModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> implements ModuleTypeWithWebFeatures {
  public static final @NonNls String WEB_MODULE = WEB_MODULE_ENTITY_TYPE_ID_NAME;

  public WebModuleTypeBase() {
    super(WEB_MODULE);
  }

  public static @NotNull WebModuleTypeBase<?> getInstance() {
    return (WebModuleTypeBase<?>)ModuleTypeManager.getInstance().findByID(WEB_MODULE);
  }

  @Override
  public @NotNull String getName() {
    return ProjectBundle.message("module.web.title");
  }

  @Override
  public @NotNull String getDescription() {
    return ProjectBundle.message("module.web.description");
  }

  @Override
  public @NotNull Icon getNodeIcon(final boolean isOpened) {
    return AllIcons.Nodes.Module;
  }

  @Override
  public boolean hasWebFeatures(@NotNull Module module) {
    return WEB_MODULE.equals(ModuleType.get(module).getId());
  }
}