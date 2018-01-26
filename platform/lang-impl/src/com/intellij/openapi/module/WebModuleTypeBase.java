/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class WebModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> implements ModuleTypeWithWebFeatures {
  @NonNls public static final String WEB_MODULE = ModuleTypeId.WEB_MODULE;

  public WebModuleTypeBase() {
    super(WEB_MODULE);
  }

  @NotNull
  @Override
  public String getName() {
    return ProjectBundle.message("module.web.title");
  }

  @NotNull
  @Override
  public String getDescription() {
    return ProjectBundle.message("module.web.description");
  }

  @Override
  public Icon getNodeIcon(final boolean isOpened) {
    return AllIcons.Nodes.Module;
  }

  /**
   * @deprecated Use {@link ModuleTypeWithWebFeatures#isAvailable}
   */
  @Deprecated
  public static boolean isWebModule(@NotNull Module module) {
    return ModuleTypeWithWebFeatures.isAvailable(module);
  }

  public boolean hasWebFeatures(@NotNull Module module) {
    return WEB_MODULE.equals(ModuleType.get(module).getId());
  }
}