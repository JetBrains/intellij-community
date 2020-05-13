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

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;

public class OrderEntryNavigatable implements Navigatable {
  private final Module myModule;
  private final OrderEntry myOrderEntry;

  public OrderEntryNavigatable(Module module, OrderEntry orderEntry) {
    myModule = module;
    myOrderEntry = orderEntry;
  }

  @Override
  public void navigate(boolean requestFocus) {
    ProjectSettingsService.getInstance(myModule.getProject()).openModuleDependenciesSettings(myModule, myOrderEntry);
  }

  @Override
  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myModule.getProject()).canOpenModuleDependenciesSettings();
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
